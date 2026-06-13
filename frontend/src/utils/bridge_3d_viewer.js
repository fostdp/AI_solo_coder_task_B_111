import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { createArchGeometry, applyHeatmapColors, createPierGeometry } from './bridgeGeometry'

export class Bridge3DViewer {
  constructor(container, options = {}) {
    this.container = container
    this.options = Object.assign({
      antialias: true,
      pixelRatio: Math.min(window.devicePixelRatio, 2),
      fov: 50,
      near: 0.1,
      far: 2000,
      initialCamera: [0, 15, 50],
      backgroundColor: 0x87ceeb,
      fogColor: 0x87ceeb,
      fogNear: 100,
      fogFar: 500
    }, options)

    this.scene = null
    this.camera = null
    this.renderer = null
    this.controls = null
    this.bridgeGroup = null
    this.sensorsGroup = null
    this.cracksGroup = null
    this.weatheringGroup = null
    this.vibrationGroup = null
    this.forceChainGroup = null
    this.archLOD = []
    this.pierLOD = []
    this.sensorObjects = []
    this.bridgeData = null
    this.lodLevel = 1
    this.showHeatmap = true
    this.showCracks = true
    this.showWeathering = false
    this.showVibration = false
    this.showForceChain = false
    this.minStress = 0
    this.maxStress = 1e6
    this.animationId = null
    this.onSensorClick = options.onSensorClick || null
    this.onBeforeRender = options.onBeforeRender || null
    this._resizeHandler = this.onResize.bind(this)
  }

  init() {
    const w = this.container.clientWidth
    const h = this.container.clientHeight

    this.scene = new THREE.Scene()
    this.scene.background = new THREE.Color(this.options.backgroundColor)
    this.scene.fog = new THREE.Fog(this.options.fogColor, this.options.fogNear, this.options.fogFar)

    this.camera = new THREE.PerspectiveCamera(this.options.fov, w / h, this.options.near, this.options.far)
    this.camera.position.set(...this.options.initialCamera)

    this.renderer = new THREE.WebGLRenderer({ antialias: this.options.antialias })
    this.renderer.setSize(w, h)
    this.renderer.setPixelRatio(this.options.pixelRatio)
    this.renderer.shadowMap.enabled = true
    this.container.appendChild(this.renderer.domElement)

    this.controls = new OrbitControls(this.camera, this.renderer.domElement)
    this.controls.enableDamping = true
    this.controls.dampingFactor = 0.05

    this._addLights()
    this._addGround()

    this.bridgeGroup = new THREE.Group()
    this.sensorsGroup = new THREE.Group()
    this.cracksGroup = new THREE.Group()
    this.weatheringGroup = new THREE.Group()
    this.vibrationGroup = new THREE.Group()
    this.forceChainGroup = new THREE.Group()
    this.scene.add(this.bridgeGroup)
    this.scene.add(this.sensorsGroup)
    this.scene.add(this.cracksGroup)
    this.scene.add(this.weatheringGroup)
    this.scene.add(this.vibrationGroup)
    this.scene.add(this.forceChainGroup)

    this._bindEvents()
    this.start()
    window.addEventListener('resize', this._resizeHandler)
    return this
  }

  _addLights() {
    const ambient = new THREE.AmbientLight(0xffffff, 0.7)
    this.scene.add(ambient)
    const dir = new THREE.DirectionalLight(0xffffff, 1.0)
    dir.position.set(30, 50, 30)
    dir.castShadow = true
    this.scene.add(dir)
  }

  _addGround() {
    const ground = new THREE.Mesh(
      new THREE.PlaneGeometry(500, 500),
      new THREE.MeshLambertMaterial({ color: 0x8b9567 })
    )
    ground.rotation.x = -Math.PI / 2
    ground.receiveShadow = true
    this.scene.add(ground)
  }

  _bindEvents() {
    const raycaster = new THREE.Raycaster()
    const mouse = new THREE.Vector2()
    this.renderer.domElement.addEventListener('click', (e) => {
      if (!this.sensorObjects.length) return
      const rect = this.renderer.domElement.getBoundingClientRect()
      mouse.x = ((e.clientX - rect.left) / rect.width) * 2 - 1
      mouse.y = -((e.clientY - rect.top) / rect.height) * 2 + 1
      raycaster.setFromCamera(mouse, this.camera)
      const hits = raycaster.intersectObjects(this.sensorObjects)
      if (hits.length > 0) {
        const sensor = hits[0].object.userData.sensor
        if (sensor && typeof this.onSensorClick === 'function') {
          this.onSensorClick(sensor)
        }
      }
    })
  }

  onResize() {
    if (!this.container || !this.camera || !this.renderer) return
    const w = this.container.clientWidth
    const h = this.container.clientHeight
    this.camera.aspect = w / h
    this.camera.updateProjectionMatrix()
    this.renderer.setSize(w, h)
  }

  start() {
    const loop = () => {
      this.animationId = requestAnimationFrame(loop)
      this.controls?.update()
      if (typeof this.onBeforeRender === 'function') this.onBeforeRender(this)
      this.renderer?.render(this.scene, this.camera)
    }
    loop()
  }

  stop() {
    if (this.animationId) cancelAnimationFrame(this.animationId)
    this.animationId = null
  }

  dispose() {
    this.stop()
    window.removeEventListener('resize', this._resizeHandler)
    this._disposeGroup(this.bridgeGroup)
    this._disposeGroup(this.sensorsGroup)
    this._disposeGroup(this.cracksGroup)
    this._disposeGroup(this.weatheringGroup)
    this._disposeGroup(this.vibrationGroup)
    this._disposeGroup(this.forceChainGroup)
    this.archLOD = []
    this.sensorObjects = []
    this.controls?.dispose()
    this.renderer?.dispose()
    if (this.renderer?.domElement?.parentNode === this.container) {
      this.container.removeChild(this.renderer.domElement)
    }
    this.scene = null
    this.camera = null
    this.renderer = null
    this.controls = null
  }

  _disposeGroup(group) {
    if (!group) return
    while (group.children.length > 0) {
      const obj = group.children[0]
      group.remove(obj)
      if (obj.isMesh || obj.isLine || obj.isSprite || obj.isPoints) {
        obj.geometry?.dispose?.()
        if (Array.isArray(obj.material)) {
          obj.material.forEach(m => m.dispose())
        } else if (obj.material) {
          if (obj.material.map) obj.material.map.dispose()
          obj.material.dispose()
        }
      }
      if (obj.children && obj.children.length) this._disposeGroup(obj)
    }
  }

  buildBridgeModels(bridge) {
    this.bridgeData = bridge
    this._disposeGroup(this.bridgeGroup)
    this.archLOD = []
    this.pierLOD = []

    const span = bridge.spanLength || 37
    const rise = span * (bridge.riseSpanRatio || 0.2)
    const t = bridge.pierThickness || 1.5
    const w = 9.6

    const lod = new THREE.LOD()
    const details = ['high', 'medium', 'low']
    const segments = [60, 36, 18]
    const distances = [10, 30, 80]

    for (let i = 0; i < 3; i++) {
      const archGeom = createArchGeometry(span, rise, t, w, segments[i], details[i])
      const mat = new THREE.MeshStandardMaterial({
        color: 0xc0b280,
        roughness: 0.9,
        metalness: 0.05,
        vertexColors: false
      })
      const mesh = new THREE.Mesh(archGeom, mat)
      mesh.castShadow = true
      mesh.receiveShadow = true
      lod.addLevel(mesh, distances[i])
      this.archLOD.push(mesh)
    }
    lod.position.y = 0
    this.bridgeGroup.add(lod)

    const pierGeom = createPierGeometry(t, rise * 0.6, t, w)
    const pierMat = new THREE.MeshStandardMaterial({ color: 0xb0a070, roughness: 0.95 })
    const leftPier = new THREE.Mesh(pierGeom, pierMat)
    leftPier.position.set(-span / 2 + t / 2, rise * 0.3, 0)
    const rightPier = new THREE.Mesh(pierGeom.clone(), pierMat)
    rightPier.position.set(span / 2 - t / 2, rise * 0.3, 0)
    this.bridgeGroup.add(leftPier)
    this.bridgeGroup.add(rightPier)

    const railingMat = new THREE.MeshStandardMaterial({ color: 0x8b7355 })
    for (let side of [-1, 1]) {
      for (let i = 0; i < 20; i++) {
        const x = -span / 2 + 2 + i * (span - 4) / 19
        const y = 4 * rise * (0.25 - (x * x) / (span * span)) + 0.6
        const post = new THREE.Mesh(new THREE.BoxGeometry(0.12, 0.8, 0.12), railingMat)
        post.position.set(x, y, side * (w / 2 - 0.3))
        this.bridgeGroup.add(post)
      }
    }
    this.setLODLevel(this.lodLevel)
  }

  setLODLevel(level) {
    this.lodLevel = level
    const lod = this.bridgeGroup?.children.find(c => c.isLOD)
    if (lod) lod.currentLevel = level
  }

  applyHeatmap(nodeData) {
    if (!this.archLOD.length || !nodeData) return
    this.minStress = Math.min(...nodeData.map(n => n.stress || 0))
    this.maxStress = Math.max(...nodeData.map(n => n.stress || 0))
    const span = this.bridgeData?.spanLength || 37
    const rise = span * (this.bridgeData?.riseSpanRatio || 0.2)
    this.archLOD.forEach(mesh => {
      applyHeatmapColors(mesh.geometry, nodeData, span, rise)
      if (mesh.material) {
        mesh.material.vertexColors = true
        mesh.material.needsUpdate = true
      }
    })
    this.showHeatmap = true
  }

  removeHeatmap() {
    this.archLOD.forEach(mesh => {
      if (mesh.material) {
        mesh.material.vertexColors = false
        mesh.material.color.set(0xc0b280)
        mesh.material.needsUpdate = true
      }
    })
    this.showHeatmap = false
  }

  toggleHeatmap(nodeData) {
    if (this.showHeatmap) this.removeHeatmap()
    else this.applyHeatmap(nodeData)
    return this.showHeatmap
  }

  placeSensors(sensors) {
    this._disposeGroup(this.sensorsGroup)
    this.sensorObjects = []
    const colors = { strain: 0x2563eb, displacement: 0x059669, crack: 0xdc2626, temperature: 0xf59e0b, vibration: 0x7c3aed }
    const span = this.bridgeData?.spanLength || 37
    const rise = span * (this.bridgeData?.riseSpanRatio || 0.2)

    sensors.forEach(s => {
      const pos = s.position || {}
      const x = pos.x != null ? pos.x : (Math.random() - 0.5) * span * 0.8
      const y = pos.y != null ? pos.y : 4 * rise * (0.25 - (x * x) / (span * span)) + 0.3
      const z = pos.z != null ? pos.z : (Math.random() - 0.5) * 4

      const geom = new THREE.SphereGeometry(0.25, 16, 16)
      const mat = new THREE.MeshBasicMaterial({
        color: colors[s.type] || 0xffffff,
        transparent: true,
        opacity: 0.9
      })
      const sphere = new THREE.Mesh(geom, mat)
      sphere.position.set(x, y, z)
      sphere.userData.sensor = s
      this.sensorsGroup.add(sphere)
      this.sensorObjects.push(sphere)

      const labelCanvas = document.createElement('canvas')
      labelCanvas.width = 128
      labelCanvas.height = 32
      const ctx = labelCanvas.getContext('2d')
      ctx.fillStyle = 'rgba(255,255,255,0.9)'
      ctx.fillRect(0, 0, 128, 32)
      ctx.fillStyle = '#374151'
      ctx.font = 'bold 12px sans-serif'
      ctx.textAlign = 'center'
      ctx.fillText(s.name || `传感器${s.id}`, 64, 20)
      const texture = new THREE.CanvasTexture(labelCanvas)
      const sprite = new THREE.Sprite(new THREE.SpriteMaterial({ map: texture }))
      sprite.scale.set(2, 0.5, 1)
      sprite.position.set(x, y + 0.8, z)
      this.sensorsGroup.add(sprite)
    })
  }

  renderCracks(cracks) {
    this._disposeGroup(this.cracksGroup)
    const mat = new THREE.LineBasicMaterial({ color: 0xdc2626, linewidth: 2, transparent: true, opacity: 0.9 })
    cracks.forEach(ck => {
      const pos = ck.position || {}
      const x = pos.x != null ? pos.x : 0
      const y = pos.y != null ? pos.y : 2
      const z = pos.z != null ? pos.z : 0
      const len = ck.length || 1

      const points = []
      const steps = 12
      for (let i = 0; i <= steps; i++) {
        const t = i / steps
        const cx = x + (t - 0.5) * len
        const cy = y + Math.sin(t * Math.PI) * 0.15
        points.push(new THREE.Vector3(cx, cy, z))
      }
      const geom = new THREE.BufferGeometry().setFromPoints(points)
      const line = new THREE.Line(geom, mat)
      line.userData.sensor = ck
      this.cracksGroup.add(line)

      const glowMat = new THREE.LineBasicMaterial({ color: 0xff4444, linewidth: 4, transparent: true, opacity: 0.3 })
      const glowLine = new THREE.Line(geom, glowMat)
      this.cracksGroup.add(glowLine)
    })
  }

  setCracksVisible(visible) {
    this.showCracks = visible
    if (this.cracksGroup) this.cracksGroup.visible = visible
  }

  getMemoryInfo() {
    const mem = this.renderer?.info?.memory
    if (!mem) return 'N/A'
    return `${mem.geometries} geom / ${mem.textures} tex`
  }

  _getWeatheringColor(depth) {
    const colors = [
      { max: 2, color: new THREE.Color(0x22c55e) },
      { max: 5, color: new THREE.Color(0x3b82f6) },
      { max: 10, color: new THREE.Color(0xeab308) },
      { max: 20, color: new THREE.Color(0xf97316) },
      { max: Infinity, color: new THREE.Color(0xef4444) }
    ]
    for (let c of colors) {
      if (depth <= c.max) return c.color
    }
    return colors[colors.length - 1].color
  }

  applyWeatheringOverlay(weatheringData) {
    if (!this.bridgeData || !weatheringData) return
    this._disposeGroup(this.weatheringGroup)

    const span = this.bridgeData.spanLength || 37
    const rise = span * (this.bridgeData.riseSpanRatio || 0.2)
    const w = 9.6

    weatheringData.forEach(point => {
      const x = point.locX != null ? point.locX : (Math.random() - 0.5) * span * 0.8
      const y = point.locY != null ? point.locY : 4 * rise * (0.25 - (x * x) / (span * span)) + 0.5
      const z = point.locZ != null ? point.locZ : (Math.random() - 0.5) * w * 0.8
      const depth = point.estimatedDepth != null ? point.estimatedDepth : 5
      const color = this._getWeatheringColor(depth)

      const radius = 0.5 + depth * 0.05
      const geom = new THREE.SphereGeometry(radius, 16, 16)
      const mat = new THREE.MeshStandardMaterial({
        color: color,
        transparent: true,
        opacity: 0.7,
        roughness: 0.8
      })
      const sphere = new THREE.Mesh(geom, mat)
      sphere.position.set(x, y, z)
      sphere.userData.weatheringPoint = point
      this.weatheringGroup.add(sphere)

      const ringGeom = new THREE.RingGeometry(radius * 1.2, radius * 1.5, 32)
      const ringMat = new THREE.MeshBasicMaterial({
        color: color,
        transparent: true,
        opacity: 0.4,
        side: THREE.DoubleSide
      })
      const ring = new THREE.Mesh(ringGeom, ringMat)
      ring.position.set(x, y, z)
      ring.lookAt(x, y + 10, z)
      this.weatheringGroup.add(ring)

      const labelCanvas = document.createElement('canvas')
      labelCanvas.width = 100
      labelCanvas.height = 30
      const ctx = labelCanvas.getContext('2d')
      ctx.fillStyle = `rgba(${Math.floor(color.r * 255)},${Math.floor(color.g * 255)},${Math.floor(color.b * 255)},0.9)`
      ctx.fillRect(0, 0, 100, 30)
      ctx.fillStyle = '#fff'
      ctx.font = 'bold 11px sans-serif'
      ctx.textAlign = 'center'
      ctx.fillText(`${depth.toFixed(1)}mm`, 50, 20)
      const texture = new THREE.CanvasTexture(labelCanvas)
      const sprite = new THREE.Sprite(new THREE.SpriteMaterial({ map: texture }))
      sprite.scale.set(1.5, 0.45, 1)
      sprite.position.set(x, y + radius + 0.5, z)
      this.weatheringGroup.add(sprite)
    })

    this.showWeathering = true
    this.weatheringGroup.visible = true
  }

  removeWeatheringOverlay() {
    this._disposeGroup(this.weatheringGroup)
    this.showWeathering = false
  }

  toggleWeatheringOverlay(weatheringData) {
    if (this.showWeathering) {
      this.removeWeatheringOverlay()
    } else {
      this.applyWeatheringOverlay(weatheringData)
    }
    return this.showWeathering
  }

  setWeatheringVisible(visible) {
    this.showWeathering = visible
    if (this.weatheringGroup) this.weatheringGroup.visible = visible
  }

  applyVibrationHeatmap(vibrationData) {
    if (!this.bridgeData || !vibrationData) return
    this._disposeGroup(this.vibrationGroup)

    const span = this.bridgeData.spanLength || 37
    const rise = span * (this.bridgeData.riseSpanRatio || 0.2)
    const w = 9.6

    const maxAccel = Math.max(...vibrationData.map(v => v.acceleration || 0), 0.01)
    const minAccel = Math.min(...vibrationData.map(v => v.acceleration || 0), 0)

    const segments = 20
    for (let i = 0; i <= segments; i++) {
      const t = i / segments
      const x = -span / 2 + t * span
      const y = 4 * rise * (0.25 - (x * x) / (span * span))

      let accel = 0
      let minDist = Infinity
      for (let v of vibrationData) {
        const vx = v.locX != null ? v.locX : 0
        const dist = Math.abs(x - vx)
        if (dist < minDist) {
          minDist = dist
          accel = v.acceleration || 0
        }
      }

      const normalized = (accel - minAccel) / (maxAccel - minAccel)
      const hue = (1 - normalized) * 0.3
      const color = new THREE.Color().setHSL(hue, 1, 0.5)

      const boxGeom = new THREE.BoxGeometry(span / segments, 0.1, w)
      const boxMat = new THREE.MeshStandardMaterial({
        color: color,
        transparent: true,
        opacity: 0.6 + normalized * 0.3,
        roughness: 0.7
      })
      const box = new THREE.Mesh(boxGeom, boxMat)
      box.position.set(x, y + 0.05, 0)
      this.vibrationGroup.add(box)

      if (i % 4 === 0) {
        const arrowLen = 0.5 + normalized * 2
        const arrowDir = new THREE.Vector3(0, 1, 0)
        const arrowOrigin = new THREE.Vector3(x, y + 0.1, 0)
        const arrow = new THREE.ArrowHelper(arrowDir, arrowOrigin, arrowLen, color.getHex(), 0.3, 0.2)
        this.vibrationGroup.add(arrow)
      }
    }

    const waveGeom = new THREE.PlaneGeometry(span, 0.5, segments, 4)
    const positions = waveGeom.attributes.position
    const colors = new Float32Array(positions.count * 3)

    for (let i = 0; i < positions.count; i++) {
      const px = positions.getX(i)
      const py = positions.getY(i)

      let accel = 0
      let minDist = Infinity
      for (let v of vibrationData) {
        const vx = v.locX != null ? v.locX : 0
        const dist = Math.abs(px - vx)
        if (dist < minDist) {
          minDist = dist
          accel = v.acceleration || 0
        }
      }
      const normalized = (accel - minAccel) / (maxAccel - minAccel)
      const hue = (1 - normalized) * 0.3
      const color = new THREE.Color().setHSL(hue, 1, 0.5)

      positions.setY(i, py + Math.sin(px * 0.5 + Date.now() * 0.001) * normalized * 0.2)
      colors[i * 3] = color.r
      colors[i * 3 + 1] = color.g
      colors[i * 3 + 2] = color.b
    }
    waveGeom.setAttribute('color', new THREE.BufferAttribute(colors, 3))

    const waveMat = new THREE.MeshStandardMaterial({
      vertexColors: true,
      transparent: true,
      opacity: 0.5,
      side: THREE.DoubleSide
    })
    const wave = new THREE.Mesh(waveGeom, waveMat)
    wave.position.set(0, rise * 0.5 + 1, -w / 2 - 1)
    wave.rotation.x = -Math.PI / 6
    this.vibrationGroup.add(wave)

    this.showVibration = true
    this.vibrationGroup.visible = true
  }

  removeVibrationHeatmap() {
    this._disposeGroup(this.vibrationGroup)
    this.showVibration = false
  }

  toggleVibrationHeatmap(vibrationData) {
    if (this.showVibration) {
      this.removeVibrationHeatmap()
    } else {
      this.applyVibrationHeatmap(vibrationData)
    }
    return this.showVibration
  }

  setVibrationVisible(visible) {
    this.showVibration = visible
    if (this.vibrationGroup) this.vibrationGroup.visible = visible
  }

  renderForceChains(forceChainData) {
    if (!this.bridgeData || !forceChainData) return
    this._disposeGroup(this.forceChainGroup)

    const span = this.bridgeData.spanLength || 37
    const rise = span * (this.bridgeData.riseSpanRatio || 0.2)

    const maxForce = Math.max(...forceChainData.map(c => c.normalForce || 1), 1)

    const stoneMat = new THREE.MeshStandardMaterial({
      color: 0xc0b280,
      roughness: 0.9,
      transparent: true,
      opacity: 0.3
    })

    const stonePositions = new Set()
    forceChainData.forEach(chain => {
      if (chain.x1 != null && chain.y1 != null) {
        stonePositions.add(`${chain.x1.toFixed(1)},${chain.y1.toFixed(1)}`)
      }
      if (chain.x2 != null && chain.y2 != null) {
        stonePositions.add(`${chain.x2.toFixed(1)},${chain.y2.toFixed(1)}`)
      }
    })

    stonePositions.forEach(posStr => {
      const [x, y] = posStr.split(',').map(Number)
      const stoneGeom = new THREE.BoxGeometry(1.2, 0.6, 8)
      const stone = new THREE.Mesh(stoneGeom, stoneMat)
      stone.position.set(x, y, 0)
      stone.castShadow = true
      this.forceChainGroup.add(stone)
    })

    forceChainData.forEach((chain, idx) => {
      const x1 = chain.x1 != null ? chain.x1 : 0
      const y1 = chain.y1 != null ? chain.y1 : 0
      const x2 = chain.x2 != null ? chain.x2 : 0
      const y2 = chain.y2 != null ? chain.y2 : 0
      const z1 = chain.z1 != null ? chain.z1 : 0
      const z2 = chain.z2 != null ? chain.z2 : 0
      const normalForce = chain.normalForce || 0

      const forceRatio = Math.min(normalForce / maxForce, 1)
      const thickness = 0.02 + forceRatio * 0.15

      const color = forceRatio > 0.7 ? new THREE.Color(0xef4444) :
                    forceRatio > 0.4 ? new THREE.Color(0xf97316) :
                    forceRatio > 0.2 ? new THREE.Color(0xeab308) : new THREE.Color(0x22c55e)

      const start = new THREE.Vector3(x1, y1, z1)
      const end = new THREE.Vector3(x2, y2, z2)
      const direction = end.clone().sub(start).normalize()
      const length = start.distanceTo(end)

      const cylGeom = new THREE.CylinderGeometry(thickness, thickness, length, 8)
      const cylMat = new THREE.MeshStandardMaterial({
        color: color,
        emissive: color,
        emissiveIntensity: forceRatio * 0.3,
        transparent: true,
        opacity: 0.8
      })
      const cylinder = new THREE.Mesh(cylGeom, cylMat)

      const midPoint = start.clone().add(end).multiplyScalar(0.5)
      cylinder.position.copy(midPoint)
      cylinder.quaternion.setFromUnitVectors(
        new THREE.Vector3(0, 1, 0),
        direction
      )
      cylinder.userData.forceChain = chain
      this.forceChainGroup.add(cylinder)

      if (forceRatio > 0.5) {
        const arrowLen = 0.5
        const arrowDir = direction.clone()
        const arrowOrigin = midPoint.clone().add(arrowDir.clone().multiplyScalar(length * 0.25))
        const arrow = new THREE.ArrowHelper(
          arrowDir,
          arrowOrigin,
          arrowLen,
          color.getHex(),
          0.2,
          0.15
        )
        this.forceChainGroup.add(arrow)

        const arrow2 = new THREE.ArrowHelper(
          arrowDir.clone().negate(),
          midPoint.clone().add(arrowDir.clone().multiplyScalar(-length * 0.25)),
          arrowLen,
          color.getHex(),
          0.2,
          0.15
        )
        this.forceChainGroup.add(arrow2)
      }

      const labelCanvas = document.createElement('canvas')
      labelCanvas.width = 80
      labelCanvas.height = 24
      const ctx = labelCanvas.getContext('2d')
      ctx.fillStyle = `rgba(${Math.floor(color.r * 255)},${Math.floor(color.g * 255)},${Math.floor(color.b * 255)},0.85)`
      ctx.fillRect(0, 0, 80, 24)
      ctx.fillStyle = '#fff'
      ctx.font = 'bold 10px sans-serif'
      ctx.textAlign = 'center'
      const displayForce = normalForce >= 1000 ? (normalForce / 1000).toFixed(1) + 'kN' : normalForce.toFixed(0) + 'N'
      ctx.fillText(displayForce, 40, 16)
      const texture = new THREE.CanvasTexture(labelCanvas)
      const sprite = new THREE.Sprite(new THREE.SpriteMaterial({ map: texture }))
      sprite.scale.set(1.2, 0.36, 1)
      sprite.position.copy(midPoint)
      sprite.position.y += 0.3
      this.forceChainGroup.add(sprite)
    })

    this.showForceChain = true
    this.forceChainGroup.visible = true
  }

  removeForceChains() {
    this._disposeGroup(this.forceChainGroup)
    this.showForceChain = false
  }

  toggleForceChains(forceChainData) {
    if (this.showForceChain) {
      this.removeForceChains()
    } else {
      this.renderForceChains(forceChainData)
    }
    return this.showForceChain
  }

  setForceChainVisible(visible) {
    this.showForceChain = visible
    if (this.forceChainGroup) this.forceChainGroup.visible = visible
  }
}

export default Bridge3DViewer
