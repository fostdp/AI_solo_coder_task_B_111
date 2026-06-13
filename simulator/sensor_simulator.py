#!/usr/bin/env python3
"""
古代石拱桥传感器模拟器
模拟 10 座石拱桥的振动传感器、位移计、裂缝计、温度传感器
每 10 分钟通过 HTTP 上报数据
支持注入应变异常和裂缝扩展数据
"""
import math
import random
import time
import json
import requests
import threading
import queue
from datetime import datetime, timedelta
from dataclasses import dataclass, field
from typing import List, Dict, Optional

API_BASE = "http://localhost:8080/api"
UPLOAD_URL = f"{API_BASE}/data/batch"
BRIDGES_URL = "http://localhost:8080/api/bridges"
SENSORS_URL = "http://localhost:8080/api/sensors"

@dataclass
class InjectionConfig:
    strain_bridge_id: Optional[int] = None
    strain_sensor_code: Optional[str] = None
    strain_magnitude: float = 0.0
    strain_duration_steps: int = 0
    strain_start_step: int = 0

    crack_bridge_id: Optional[int] = None
    crack_sensor_code: Optional[str] = None
    crack_growth_rate: float = 0.0
    crack_start_step: int = 0

    _injection_queue: 'queue.Queue' = field(default_factory=queue.Queue)

    def is_active(self) -> bool:
        return self.strain_bridge_id is not None or self.crack_bridge_id is not None or not self._injection_queue.empty()

    def add_injection(self, injection: dict):
        self._injection_queue.put(injection)

    def process_queue(self, current_step: int):
        while not self._injection_queue.empty():
            injection = self._injection_queue.get()
            itype = injection.get('type')
            if itype == 'strain':
                self.strain_bridge_id = injection.get('bridge_id')
                self.strain_sensor_code = injection.get('sensor_code')
                self.strain_magnitude = injection.get('magnitude', 50.0)
                self.strain_duration_steps = injection.get('duration_steps', 6)
                self.strain_start_step = current_step
                print(f"[注入] 应变异常: 桥={self.strain_bridge_id}, 传感器={self.strain_sensor_code}, 幅度={self.strain_magnitude}με, 持续={self.strain_duration_steps}步")
            elif itype == 'crack':
                self.crack_bridge_id = injection.get('bridge_id')
                self.crack_sensor_code = injection.get('sensor_code')
                self.crack_growth_rate = injection.get('growth_rate', 0.01)
                self.crack_start_step = current_step
                print(f"[注入] 裂缝扩展: 桥={self.crack_bridge_id}, 传感器={self.crack_sensor_code}, 速率={self.crack_growth_rate}mm/步")

BRIDGE_CONFIGS = [
    {"id": 1, "name": "赵州桥", "spans": 1, "base_strain": 80, "base_settlement": 2.5, "base_crack": 1.2},
    {"id": 2, "name": "卢沟桥", "spans": 11, "base_strain": 95, "base_settlement": 4.2, "base_crack": 2.1},
    {"id": 3, "name": "广济桥", "spans": 19, "base_strain": 70, "base_settlement": 3.0, "base_crack": 1.5},
    {"id": 4, "name": "洛阳桥", "spans": 46, "base_strain": 60, "base_settlement": 3.5, "base_crack": 1.0},
    {"id": 5, "name": "宝带桥", "spans": 53, "base_strain": 85, "base_settlement": 5.8, "base_crack": 3.2},
    {"id": 6, "name": "安平桥", "spans": 362, "base_strain": 55, "base_settlement": 4.0, "base_crack": 1.8},
    {"id": 7, "name": "五亭桥", "spans": 3, "base_strain": 75, "base_settlement": 2.0, "base_crack": 0.8},
    {"id": 8, "name": "铁索桥泸定桥", "spans": 1, "base_strain": 120, "base_settlement": 0.5, "base_crack": 0.2},
    {"id": 9, "name": "程阳风雨桥", "spans": 5, "base_strain": 65, "base_settlement": 3.8, "base_crack": 1.1},
    {"id": 10, "name": "拱宸桥", "spans": 3, "base_strain": 90, "base_settlement": 5.0, "base_crack": 2.0},
]

SENSOR_TYPES = {
    "strain": {"unit": "μ ε", "name_prefix": "应变计", "count_per_arch": 4},
    "displacement": {"unit": "mm", "name_prefix": "位移计", "count_per_pier": 2},
    "crack": {"unit": "mm", "name_prefix": "裂缝计", "count_per_arch": 1},
    "temperature": {"unit": "°C", "name_prefix": "温度传感器", "count_total": 2},
    "vibration": {"unit": "mm/s²", "name_prefix": "振动传感器", "count_per_arch": 2},
}


@dataclass
class SensorSim:
    id: int
    code: str
    bridge_id: int
    type: str
    name: str
    base_value: float
    noise: float
    trend: float = 0.0
    last_value: float = 0.0
    position: Dict[str, float] = field(default_factory=dict)
    forced_value: Optional[float] = None

    def generate(self, t: float, temperature: float, injection: Optional[InjectionConfig] = None, current_step: int = 0) -> float:
        season = math.sin(2 * math.pi * (t / 365.0))
        daily = math.sin(2 * math.pi * (t * 24 % 24 / 24.0))
        temp_effect = 0 if self.type == "temperature" else (temperature - 15) * 0.8
        drift = self.trend * t

        if self.type == "strain":
            value = self.base_value + season * 15 + daily * 3 + temp_effect * 0.5 + drift
        elif self.type == "displacement":
            value = self.base_value + season * 1.5 + daily * 0.3 + drift
        elif self.type == "crack":
            value = self.base_value + season * 0.2 + daily * 0.05 + drift * 1.2
        elif self.type == "temperature":
            value = 15 + season * 18 + daily * 5
        elif self.type == "vibration":
            value = 0.5 + abs(daily) * 0.8 + random.gauss(0, 0.1)
        else:
            value = self.base_value

        value += random.gauss(0, self.noise)

        if injection:
            if injection.strain_bridge_id == self.bridge_id and self.type == "strain":
                if injection.strain_sensor_code is None or injection.strain_sensor_code == self.code:
                    step_offset = current_step - injection.strain_start_step
                    if 0 <= step_offset < injection.strain_duration_steps:
                        ramp = math.sin(math.pi * step_offset / injection.strain_duration_steps)
                        value += injection.strain_magnitude * ramp

            if injection.crack_bridge_id == self.bridge_id and self.type == "crack":
                if injection.crack_sensor_code is None or injection.crack_sensor_code == self.code:
                    step_offset = current_step - injection.crack_start_step
                    if step_offset >= 0:
                        value += injection.crack_growth_rate * step_offset

        self.last_value = max(0, value) if self.type != "temperature" else value
        return self.last_value


class BridgeSimulator:
    def __init__(self, bridge_cfg: dict, start_time: datetime = None):
        self.cfg = bridge_cfg
        self.bridge_id = bridge_cfg["id"]
        self.bridge_name = bridge_cfg["name"]
        self.sensors: List[SensorSim] = []
        self.t = 0.0
        self._init_sensors()

    def _init_sensors(self):
        sid = (self.bridge_id - 1) * 100
        spans = self.cfg["spans"]
        piers = spans + 1

        n_strain = min(spans * 4, 16)
        for i in range(n_strain):
            sid += 1
            self.sensors.append(SensorSim(
                id=sid,
                code=f"STRAIN-{self.bridge_id}-{i+1:02d}",
                bridge_id=self.bridge_id,
                type="strain",
                name=f"拱券应变计-{i+1}",
                base_value=self.cfg["base_strain"] * (0.8 + 0.4 * random.random()),
                noise=2.0,
                trend=0.05 * (random.random() - 0.3),
                position={"x": (i / n_strain - 0.5) * 30, "y": 5 + random.random() * 2, "z": random.random() * 4 - 2}
            ))

        for i in range(min(piers * 2, 8)):
            sid += 1
            self.sensors.append(SensorSim(
                id=sid,
                code=f"DISP-{self.bridge_id}-{i+1:02d}",
                bridge_id=self.bridge_id,
                type="displacement",
                name=f"桥墩位移计-{i+1}",
                base_value=self.cfg["base_settlement"] * (0.7 + 0.6 * random.random()),
                noise=0.15,
                trend=0.02 * (random.random() + 0.5),
                position={"x": -15 + i * 5, "y": 0.5, "z": random.random() * 3 - 1.5}
            ))

        n_crack = max(2, int(spans * 0.5))
        for i in range(min(n_crack, 6)):
            sid += 1
            self.sensors.append(SensorSim(
                id=sid,
                code=f"CRACK-{self.bridge_id}-{i+1:02d}",
                bridge_id=self.bridge_id,
                type="crack",
                name=f"裂缝计-{i+1}",
                base_value=self.cfg["base_crack"] * (0.5 + random.random()),
                noise=0.05,
                trend=0.003 + 0.01 * random.random(),
                position={"x": (random.random() - 0.5) * 20, "y": 2 + random.random() * 3, "z": random.random() * 3 - 1.5}
            ))

        for i in range(2):
            sid += 1
            self.sensors.append(SensorSim(
                id=sid,
                code=f"TEMP-{self.bridge_id}-{i+1:02d}",
                bridge_id=self.bridge_id,
                type="temperature",
                name=f"环境温度-{i+1}",
                base_value=15.0,
                noise=0.3,
                position={"x": 0, "y": 8, "z": i * 3 - 1.5}
            ))

        for i in range(min(spans * 2, 8)):
            sid += 1
            self.sensors.append(SensorSim(
                id=sid,
                code=f"VIB-{self.bridge_id}-{i+1:02d}",
                bridge_id=self.bridge_id,
                type="vibration",
                name=f"振动传感器-{i+1}",
                base_value=0.5,
                noise=0.1,
                position={"x": -14 + i * 4, "y": 4, "z": random.random() * 2 - 1}
            ))

    def step(self, dt_days: float = 1.0 / 144, injection: Optional[InjectionConfig] = None, current_step: int = 0):
        self.t += dt_days
        temp_sensor = next((s for s in self.sensors if s.type == "temperature"), None)
        temp = temp_sensor.generate(self.t, 15, injection, current_step) if temp_sensor else 15
        readings = []
        for s in self.sensors:
            if s.type == "temperature":
                continue
            val = s.generate(self.t, temp, injection, current_step)
            readings.append({
                "sensorCode": s.code,
                "bridgeId": self.bridge_id,
                "value": round(val, 4),
                "temperature": round(temp, 2),
                "timestamp": None
            })
        if temp_sensor:
            readings.append({
                "sensorCode": temp_sensor.code,
                "bridgeId": self.bridge_id,
                "value": round(temp, 2),
                "temperature": round(temp, 2),
                "timestamp": None
            })
        return readings


class HeritageSimulator:
    def __init__(self):
        self.bridges = [BridgeSimulator(cfg) for cfg in BRIDGE_CONFIGS]
        self.current_time = datetime.now()
        self.injection = InjectionConfig()
        self.current_step = 0
        self._stop_event = threading.Event()

    def step_all(self, dt_minutes: int = 10):
        dt_days = dt_minutes / (24 * 60)
        self.current_time += timedelta(minutes=dt_minutes)
        self.current_step += 1
        self.injection.process_queue(self.current_step)

        all_readings = []
        for b in self.bridges:
            readings = b.step(dt_days, self.injection, self.current_step)
            ts = self.current_time.strftime("%Y-%m-%dT%H:%M:%S")
            for r in readings:
                r["timestamp"] = ts
            all_readings.extend(readings)
        return all_readings

    def upload(self, data: list) -> bool:
        try:
            resp = requests.post(UPLOAD_URL, json=data, timeout=10)
            if resp.status_code == 200:
                result = resp.json()
                print(f"[{self.current_time}] 上报成功: {len(data)} 条, code={resp.status_code}, count={result.get('data', '?')}")
                return True
            else:
                print(f"[{self.current_time}] 上报失败: HTTP {resp.status_code}: {resp.text[:200]}")
                return False
        except Exception as e:
            print(f"[{self.current_time}] 上报异常: {e}")
            return False

    def _interactive_console(self):
        """交互式控制台线程，用于动态注入数据"""
        print("\n=== 交互控制台启动 ===")
        print("可用命令:")
        print("  strain <bridge_id> [sensor_code] [magnitude] [duration_steps]")
        print("         - 注入应变异常，例: strain 1 ST-001-001 80 6")
        print("  crack <bridge_id> [sensor_code] [growth_rate]")
        print("         - 注入裂缝扩展，例: crack 1 CK-001-001 0.02")
        print("  list bridges   - 列出所有桥梁")
        print("  list sensors <bridge_id> - 列出某桥所有传感器")
        print("  status         - 查看当前状态")
        print("  help           - 显示帮助")
        print("  quit           - 退出\n")

        while not self._stop_event.is_set():
            try:
                cmd = input("> ").strip()
                if not cmd:
                    continue
                parts = cmd.split()
                if parts[0] == 'quit' or parts[0] == 'exit':
                    self._stop_event.set()
                    break
                elif parts[0] == 'help':
                    print("  strain <bridge_id> [sensor_code] [magnitude] [duration_steps]")
                    print("  crack <bridge_id> [sensor_code] [growth_rate]")
                    print("  list bridges")
                    print("  list sensors <bridge_id>")
                    print("  status")
                elif parts[0] == 'strain' and len(parts) >= 2:
                    injection = {'type': 'strain', 'bridge_id': int(parts[1])}
                    if len(parts) >= 3 and parts[2] != 'all':
                        injection['sensor_code'] = parts[2]
                    if len(parts) >= 4:
                        injection['magnitude'] = float(parts[3])
                    if len(parts) >= 5:
                        injection['duration_steps'] = int(parts[4])
                    self.injection.add_injection(injection)
                elif parts[0] == 'crack' and len(parts) >= 2:
                    injection = {'type': 'crack', 'bridge_id': int(parts[1])}
                    if len(parts) >= 3 and parts[2] != 'all':
                        injection['sensor_code'] = parts[2]
                    if len(parts) >= 4:
                        injection['growth_rate'] = float(parts[3])
                    self.injection.add_injection(injection)
                elif parts[0] == 'list' and len(parts) >= 2:
                    if parts[1] == 'bridges':
                        for b in BRIDGE_CONFIGS:
                            print(f"  桥{b['id']}: {b['name']}, 跨数={b['spans']}")
                    elif parts[1] == 'sensors' and len(parts) >= 3:
                        bid = int(parts[2])
                        bridge = next((b for b in self.bridges if b.bridge_id == bid), None)
                        if bridge:
                            for s in bridge.sensors:
                                print(f"  {s.code}: {s.name}, 类型={s.type}")
                        else:
                            print(f"未找到桥 {bid}")
                elif parts[0] == 'status':
                    print(f"当前步数: {self.current_step}")
                    print(f"当前时间: {self.current_time}")
                    print(f"活动注入: {self.injection.is_active()}")
                    if self.injection.strain_bridge_id:
                        print(f"  应变异常: 桥{self.injection.strain_bridge_id}, 幅度={self.injection.strain_magnitude}")
                    if self.injection.crack_bridge_id:
                        print(f"  裂缝扩展: 桥{self.injection.crack_bridge_id}, 速率={self.injection.crack_growth_rate}")
                else:
                    print(f"未知命令: {cmd}，输入 help 查看帮助")
            except Exception as e:
                print(f"命令执行错误: {e}")

    def run_realtime(self, speedup: int = 1, interactive: bool = False):
        print(f"=== 古桥传感器模拟器启动 ===")
        print(f"监测桥梁数: {len(self.bridges)}")
        total_sensors = sum(len(b.sensors) for b in self.bridges)
        print(f"传感器总数: {total_sensors}")
        print(f"上报频率: 每 10 分钟 (加速 {speedup}x)")
        print(f"API 地址: {UPLOAD_URL}")
        print(f"交互模式: {'开启' if interactive else '关闭'}")
        print("=" * 40)

        if interactive:
            console_thread = threading.Thread(target=self._interactive_console, daemon=True)
            console_thread.start()

        interval_sec = 600 / speedup
        try:
            while not self._stop_event.is_set():
                data = self.step_all(10)
                self.upload(data)
                time.sleep(interval_sec)
        except KeyboardInterrupt:
            print("\n模拟器已停止")
            self._stop_event.set()

    def run_historcal(self, days: int = 365):
        """历史数据回灌：从1年前开始，每10分钟一条，共 ~52560 条/桥"""
        print(f"开始回灌 {days} 天历史数据...")
        start = datetime.now() - timedelta(days=days)
        self.current_time = start
        total = 0
        steps = days * 24 * 6  # 每天144个10分钟
        batch_size = 500
        batch = []

        for i in range(steps):
            data = self.step_all(10)
            batch.extend(data)
            if len(batch) >= batch_size:
                self.upload(batch)
                total += len(batch)
                batch = []
            if i % 100 == 0:
                progress = i / steps * 100
                print(f"进度: {progress:.1f}% ({i}/{steps}), 已上报 {total} 条")

        if batch:
            self.upload(batch)
            total += len(batch)
        print(f"历史数据回灌完成, 共 {total} 条")


def main():
    import argparse
    parser = argparse.ArgumentParser(description="古代石拱桥传感器模拟器")
    parser.add_argument("--mode", choices=["realtime", "historical"], default="realtime",
                        help="运行模式: realtime实时模拟, historical历史数据回灌")
    parser.add_argument("--speedup", type=int, default=1, help="实时模式加速倍数")
    parser.add_argument("--days", type=int, default=365, help="历史模式回灌天数")
    parser.add_argument("--api", default="http://backend:8080/api/data", help="API基础地址")
    parser.add_argument("--interactive", action="store_true", help="启用交互式控制台")
    parser.add_argument("--inject-strain", metavar="BRIDGE_ID", type=int,
                        help="启动时注入应变异常到指定桥")
    parser.add_argument("--inject-crack", metavar="BRIDGE_ID", type=int,
                        help="启动时注入裂缝扩展到指定桥")
    parser.add_argument("--strain-magnitude", type=float, default=60.0,
                        help="应变异常幅度 (单位: με)")
    parser.add_argument("--strain-duration", type=int, default=6,
                        help="应变异常持续步数 (每步10分钟)")
    parser.add_argument("--crack-rate", type=float, default=0.01,
                        help="裂缝扩展速率 (单位: mm/步)")
    args = parser.parse_args()

    global UPLOAD_URL
    UPLOAD_URL = f"{args.api}/batch"

    sim = HeritageSimulator()

    if args.inject_strain:
        sim.injection.add_injection({
            'type': 'strain',
            'bridge_id': args.inject_strain,
            'magnitude': args.strain_magnitude,
            'duration_steps': args.strain_duration
        })

    if args.inject_crack:
        sim.injection.add_injection({
            'type': 'crack',
            'bridge_id': args.inject_crack,
            'growth_rate': args.crack_rate
        })

    if args.mode == "historical":
        sim.run_historcal(args.days)
    else:
        sim.run_realtime(args.speedup, args.interactive)


if __name__ == "__main__":
    main()
