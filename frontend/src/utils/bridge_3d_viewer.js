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
    this.archLOD = []
    this.pierLOD = []
    this.sensorObjects = []
    this.bridgeData = null
    this.lodLevel = 1
    this.showHeatmap = true
    this.showCracks = true
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
    this.scene.add(this.bridgeGroup)
    this.scene.add(this.sensorsGroup)
    this.scene.add(this.cracksGroup)

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
}

export default Bridge3DViewer
