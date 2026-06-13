import * as THREE from 'three'

const colorStops = [
  { t: 0.0, r: 0,    g: 0,    b: 1    },
  { t: 0.25,r: 0,    g: 1,    b: 1    },
  { t: 0.5, r: 0,    g: 1,    b: 0    },
  { t: 0.75,r: 1,    g: 1,    b: 0    },
  { t: 1.0, r: 1,    g: 0,    b: 0    }
]

export function heatColor(value, min, max) {
  if (min === max) return new THREE.Color(0.5, 0.5, 0.5)
  const t = Math.min(1, Math.max(0, (value - min) / (max - min)))
  for (let i = 0; i < colorStops.length - 1; i++) {
    const a = colorStops[i], b = colorStops[i + 1]
    if (t >= a.t && t <= b.t) {
      const k = (t - a.t) / (b.t - a.t)
      return new THREE.Color(
        a.r + (b.r - a.r) * k,
        a.g + (b.g - a.g) * k,
        a.b + (b.b - a.b) * k
      )
    }
  }
  return new THREE.Color(1, 0, 0)
}

export function simplifyGeometry(geometry, targetRatio = 0.4) {
  if (!geometry.attributes.position || !geometry.index) return geometry
  const positions = geometry.attributes.position.array
  const indices = geometry.index.array
  const faceCount = indices.length / 3
  const targetFaces = Math.max(200, Math.floor(faceCount * targetRatio))

  if (faceCount <= targetFaces) return geometry

  const edgeErrors = new Map()
  const faceError = new Array(faceCount).fill(0)
  const vertexError = new Array(positions.length / 3).fill(0)

  for (let fi = 0; fi < faceCount; fi++) {
    const i0 = indices[fi * 3], i1 = indices[fi * 3 + 1], i2 = indices[fi * 3 + 2]
    const err = Math.abs(
      (positions[i1 * 3 + 1] - positions[i0 * 3 + 1]) +
      (positions[i2 * 3 + 1] - positions[i0 * 3 + 1])
    ) / 1000
    faceError[fi] = err
    vertexError[i0] += err
    vertexError[i1] += err
    vertexError[i2] += err
  }

  const removed = new Set()
  const sortedVerts = Array.from({ length: positions.length / 3 }, (_, i) => i)
    .sort((a, b) => vertexError[a] - vertexError[b])

  let removedCount = 0
  const targetRemove = Math.floor((faceCount - targetFaces) / 3)
  for (let i = 0; i < sortedVerts.length && removedCount < targetRemove; i++) {
    if (vertexError[sortedVerts[i]] < 0.001) {
      removed.add(sortedVerts[i])
      removedCount++
    }
  }

  const newIndices = []
  for (let fi = 0; fi < faceCount; fi++) {
    const i0 = indices[fi * 3], i1 = indices[fi * 3 + 1], i2 = indices[fi * 3 + 2]
    if (removed.has(i0) || removed.has(i1) || removed.has(i2)) continue
    newIndices.push(i0, i1, i2)
  }

  const newGeom = new THREE.BufferGeometry()
  newGeom.setAttribute('position', geometry.attributes.position)
  newGeom.setAttribute('normal', geometry.attributes.normal)
  newGeom.setAttribute('uv', geometry.attributes.uv)
  newGeom.setIndex(newIndices)
  newGeom.computeVertexNormals()
  return newGeom
}

export function createArchGeometry(span, rise, thickness, width, segments = 60, detail = 'high') {
  const seg = detail === 'high' ? segments : detail === 'medium' ? Math.floor(segments * 0.6) : Math.floor(segments * 0.3)
  const t = thickness
  const w = width
  const s = span
  const r = rise
  const shape = new THREE.Shape()

  shape.moveTo(-s / 2, 0)
  for (let i = 1; i <= seg; i++) {
    const x = -s / 2 + (s * i) / seg
    const y = 4 * r * (0.25 - (x * x) / (s * s))
    shape.lineTo(x, y)
  }
  shape.lineTo(s / 2 - t, 0)

  const innerShape = new THREE.Path()
  innerShape.moveTo(-s / 2 + t, t * 0.6)
  for (let i = 1; i <= seg; i++) {
    const x = -s / 2 + t + (s - 2 * t) * i / seg
    const y = 4 * r * (0.25 - (x * x) / (s * s)) - t * 0.8
    innerShape.lineTo(x, Math.max(t * 0.3, y))
  }
  innerShape.lineTo(s / 2 - t, t * 0.3)
  innerShape.lineTo(-s / 2 + t, t * 0.6)
  shape.holes.push(innerShape)

  const extrudeSettings = { depth: w, bevelEnabled: false, curveSegments: 8 }
  const geometry = new THREE.ExtrudeGeometry(shape, extrudeSettings)
  geometry.rotateX(-Math.PI / 2)
  geometry.translate(0, 0, -w / 2)
  geometry.computeVertexNormals()
  return geometry
}

export function applyHeatmapColors(geometry, stressData, span, rise) {
  if (!geometry.attributes.position) return geometry
  const positions = geometry.attributes.position.array
  const count = positions.length / 3
  const colors = new Float32Array(count * 3)

  let min = Infinity, max = -Infinity
  for (const s of stressData) {
    const sv = s.stress != null ? s.stress : 0
    min = Math.min(min, sv)
    max = Math.max(max, sv)
  }

  for (let i = 0; i < count; i++) {
    const x = positions[i * 3]
    const z = positions[i * 3 + 2]
    const absX = Math.abs(x)
    const normX = absX / (span / 2)
    const estY = 4 * rise * (0.25 - normX * normX / 4)

    let stressEst = 0
    for (let j = 0; j < stressData.length; j++) {
      const sd = stressData[j]
      const sx = sd.x != null ? sd.x : 0
      const dist = Math.abs(sx - x)
      const weight = Math.max(0.1, 1 / (1 + dist * 0.5))
      stressEst += (sd.stress != null ? sd.stress : 0) * weight
    }
    stressEst = stressEst / stressData.length * 1.5
    stressEst = min + (max - min) * (0.5 + 0.5 * Math.sin(estY / rise * Math.PI))

    const c = heatColor(stressEst, min, max)
    colors[i * 3] = c.r
    colors[i * 3 + 1] = c.g
    colors[i * 3 + 2] = c.b
  }
  geometry.setAttribute('color', new THREE.BufferAttribute(colors, 3))
  return geometry
}

export function createPierGeometry(width, height, thickness, depth) {
  return new THREE.BoxGeometry(width, height, depth)
}
