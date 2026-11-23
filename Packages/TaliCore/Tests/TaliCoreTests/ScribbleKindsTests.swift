import Foundation
import Testing
@testable import TaliCore

/// Scribble to delete over many kinds of scribble and of ordinary writing, each drawn 300 ways
/// (size, angle, slant, wobble, how even the legs are): how often each counts as a scribble. Writing
/// that counts still deletes nothing unless its path runs over something (`ScratchOut.takes`).
/// Android's `ScribbleKindsTest`, with a SplitMix generator standing in for Kotlin's `Random`.
struct ScribbleKindsTests {
  final class Rnd {
    var state: UInt64
    init(_ seed: Int) { state = UInt64(seed) }
    func next() -> UInt64 {
      state &+= 0x9E37_79B9_7F4A_7C15
      var z = state
      z = (z ^ (z >> 30)) &* 0xBF58_476D_1CE4_E5B9
      z = (z ^ (z >> 27)) &* 0x94D0_49BB_1331_11EB
      return z ^ (z >> 31)
    }
    func unit() -> Float { Float(next() >> 40) / Float(1 << 24) }
    /// A whole number in a..<b.
    func int(_ a: Int, _ b: Int) -> Int { a + Int(next() % UInt64(b - a)) }
    func f(_ a: Float, _ b: Float) -> Float { a + unit() * (b - a) }
  }

  static let tau = 2 * Float.pi

  /// Through [corners] with rounded turns, a point every ~2 units, turned by [angle] and wobbled.
  static func poly(_ corners: [Pt], _ r: Rnd, _ angle: Float, jitter: Float = 0.6) -> [Pt] {
    var out: [Pt] = []
    for (a, b) in zip(corners, corners.dropFirst()) {
      let n = max(Int(a.dist(b) / 2), 2)
      for k in 0..<n { out.append(a + (b - a) * (Float(k) / Float(n))) }
    }
    if let last = corners.last { out.append(last) }
    // Round the corners a little like a hand does (moving average).
    let smooth = out.indices.map { i -> Pt in
      let lo = max(0, i - 2), hi = min(out.count - 1, i + 2)
      let sum = out[lo...hi].reduce(Pt.zero, +)
      return sum / Float(hi - lo + 1)
    }
    return turned(smooth, r, angle, jitter: jitter)
  }

  static func turned(_ pts: [Pt], _ r: Rnd, _ angle: Float, jitter: Float = 0.6) -> [Pt] {
    let c = cosf(angle), s = sinf(angle)
    return pts.map { p in
      let jx = (r.unit() - 0.5) * jitter
      let jy = (r.unit() - 0.5) * jitter
      return Pt(p.x * c - p.y * s + jx, p.x * s + p.y * c + jy)
    }
  }

  // MARK: Scribbles

  /// Back and forth along a line, moving on a little each leg, uneven lengths.
  static func scrub(_ r: Rnd) -> [Pt] {
    let legs = r.int(5, 14)
    let len = r.f(25, 140)
    let drift = len * r.f(0, 0.22)
    let corners = (0...legs).map { i -> Pt in
      let x = (i % 2 == 0 ? 0 : len) + r.f(-0.15, 0.15) * len
      let y = Float(i) * drift + r.f(-0.05, 0.05) * len
      return Pt(x, y)
    }
    return poly(corners, r, r.f(0, tau))
  }

  /// Up and down across a word, travelling sideways (a slanted zigzag).
  static func zigzagAcross(_ r: Rnd) -> [Pt] {
    let legs = r.int(6, 18)
    let h = r.f(20, 70)
    let step = h * r.f(0.05, 0.3)
    let slant = r.f(-0.3, 1.1)
    let corners = (0...legs).map { i -> Pt in
      let y = i % 2 == 0 ? 0 : h * r.f(0.75, 1.1)
      return Pt(Float(i) * step + y * slant, y)
    }
    return poly(corners, r, r.f(-0.4, 0.4))
  }

  /// Loops travelling sideways ("eeee" / "llll" scrubbing), slanted.
  static func loops(_ r: Rnd) -> [Pt] {
    let n = r.int(3, 10)
    let h = r.f(20, 70)
    let w = Float(n) * h * r.f(0.15, 0.5)
    let slant = r.f(-0.3, 1.1)
    let m = n * 40
    let pts = (0...m).map { i -> Pt in
      let t = Float(i) / Float(m) * Float(n) * tau
      let x = w * Float(i) / Float(m) + w / Float(n) * r.f(0.3, 0.45) * sinf(t)
      let y = h / 2 * cosf(t) * (1 + 0.2 * sinf(t * 0.37))
      return Pt(x + y * slant, y)
    }
    return turned(pts, r, r.f(-0.4, 0.4))
  }

  /// Round and round over the same spot, drifting (a tangle).
  static func tangle(_ r: Rnd) -> [Pt] {
    let n = r.int(3, 8)
    let rad = r.f(12, 40)
    let travel = rad * r.f(0, 2.5)
    let m = n * 40
    let pts = (0...m).map { i -> Pt in
      let t = Float(i) / Float(m) * Float(n) * tau
      let rr = rad * (1 + 0.25 * sinf(t * 0.53))
      return Pt(travel * Float(i) / Float(m) + rr * cosf(t), rr * 0.8 * sinf(t))
    }
    return turned(pts, r, r.f(0, tau))
  }

  /// Scrubbed in place: very short drift, many legs.
  static func inPlace(_ r: Rnd) -> [Pt] {
    let legs = r.int(6, 16)
    let len = r.f(20, 90)
    let corners = (0...legs).map { i -> Pt in
      let x = (i % 2 == 0 ? 0 : len) + r.f(-0.2, 0.2) * len
      return Pt(x, r.f(-0.08, 0.08) * len)
    }
    return poly(corners, r, r.f(0, tau), jitter: 1.2)
  }

  // MARK: Not scribbles

  /// A word of joined letters: humps, tall loops, small loops, vees and o's, mixed heights.
  static func word(_ r: Rnd) -> [Pt] {
    let xh = r.f(10, 30)
    let letters = r.int(3, 9)
    var pts: [Pt] = []
    var x: Float = 0
    for _ in 0..<letters {
      let kind = r.int(0, 5)
      let w = xh * r.f(0.5, 1)
      let h = kind == 1 ? xh * r.f(1.8, 2.4) : xh
      for i in 0...30 {
        let t = Float(i) / 30
        switch kind {
        case 0: pts.append(Pt(x + w * t, -h * abs(sinf(t * .pi))))  // hump (n, m)
        case 1: pts.append(Pt(x + w * t + w * 0.3 * sinf(t * tau), -h * sinf(t * .pi)))  // tall loop (l, h)
        case 2: pts.append(Pt(x + w * t + w * 0.35 * sinf(t * tau), -h * 0.5 * (1 - cosf(t * tau))))  // small loop (e)
        case 3: pts.append(Pt(x + w * t, -h * abs(1 - 2 * t)))  // vee (v, w)
        default: pts.append(Pt(x + w * 0.5 - w * 0.5 * cosf(t * tau), -h * 0.5 + h * 0.5 * sinf(t * tau)))  // o
        }
      }
      x += w * r.f(1, 1.4)
    }
    return turned(pts, r, r.f(-0.3, 0.3))
  }

  /// Cursive runs of one letter: mmm, nnn, www, lll, eee (the closest writing gets to a scribble).
  static func run(_ r: Rnd) -> [Pt] {
    let xh = r.f(10, 30)
    let n = r.int(3, 7)
    let kind = r.int(0, 4)
    let m = n * 30
    let w = xh * r.f(0.6, 1.1)
    let pts = (0...m).map { i -> Pt in
      let t = Float(i) / 30
      switch kind {
      case 0: return Pt(w * t, -xh * abs(sinf(t * .pi)))  // m/n humps
      case 1: return Pt(w * t, -xh * abs(1 - 2 * (t - Float(Int(t)))))  // w vees
      case 2: return Pt(w * 0.6 * t + w * 0.25 * sinf(t * tau), -xh * 2 * abs(sinf(t * .pi)))  // l loops
      default: return Pt(w * t + w * 0.35 * sinf(t * tau), -xh * 0.5 * (1 - cosf(t * tau)))  // e loops
      }
    }
    return turned(pts, r, r.f(-0.3, 0.3))
  }

  /// A spring drawn in an engineering diagram: loops spaced along a line.
  static func spring(_ r: Rnd) -> [Pt] {
    let n = r.int(4, 9)
    let h = r.f(15, 40)
    let pitch = h * r.f(0.4, 0.9)
    let m = n * 40
    let pts = (0...m).map { i -> Pt in
      let t = Float(i) / Float(m) * Float(n) * tau
      return Pt(pitch * Float(n) * Float(i) / Float(m) + pitch * 0.45 * sinf(t), h / 2 * cosf(t))
    }
    return turned(pts, r, r.f(0, tau))
  }

  /// A resistor zigzag: even, wide-spaced legs.
  static func resistor(_ r: Rnd) -> [Pt] {
    let legs = r.int(5, 9)
    let h = r.f(10, 30)
    let step = h * r.f(0.45, 0.9)
    let corners = (0...legs).map { i in Pt(Float(i) * step, i % 2 == 0 ? -h / 2 : h / 2) }
    return poly(corners, r, r.f(0, tau))
  }

  /// A star drawn in one stroke.
  static func star(_ r: Rnd) -> [Pt] {
    let rad = r.f(15, 50)
    let corners = (0...5).map { i -> Pt in
      let a = Float(i) * 4 * .pi / 5
      return Pt(rad * cosf(a), rad * sinf(a))
    }
    return poly(corners, r, r.f(0, tau))
  }

  /// A wavy underline.
  static func wave(_ r: Rnd) -> [Pt] {
    let len = r.f(60, 300)
    let waves = r.int(3, 10)
    let amp = len / Float(waves) * r.f(0.1, 0.5)
    let pts = (0...200).map { i -> Pt in
      let t = Float(i) / 200
      return Pt(len * t, amp * sinf(t * Float(waves) * tau))
    }
    return turned(pts, r, r.f(-0.2, 0.2))
  }

  /// A spiral.
  static func spiral(_ r: Rnd) -> [Pt] {
    let turns = r.f(1.5, 4)
    let m = Int(turns * 60)
    let pts = (0...m).map { i -> Pt in
      let t = Float(i) / 60 * tau
      let rr = 3 + Float(i) * r.f(0.2, 0.5)
      return Pt(rr * cosf(t), rr * sinf(t))
    }
    return turned(pts, r, 0)
  }

  static func rate(_ gen: (Rnd) -> [Pt]) -> Float {
    let n = 300
    let hits = (0..<n).filter { seed in ScratchOut.area(gen(Rnd(seed * 31 + 7)), zoom: 1) != nil }.count
    return Float(hits) * 100 / Float(n)
  }

  @Test func rates() {
    let scribbles: [(String, (Rnd) -> [Pt], Float)] = [
      ("scrub", Self.scrub, 95), ("zigzag across", Self.zigzagAcross, 65), ("loops", Self.loops, 95),
      ("tangle", Self.tangle, 95), ("in place", Self.inPlace, 95),
    ]
    let writing: [(String, (Rnd) -> [Pt], Float)] = [
      ("word", Self.word, 10), ("letter run", Self.run, 30), ("spring", Self.spring, 25), ("resistor", Self.resistor, 2),
      ("star", Self.star, 2), ("wave", Self.wave, 2), ("spiral", Self.spiral, 3),
    ]
    var report = ""
    for (name, gen, least) in scribbles {
      let r = Self.rate(gen)
      report += "scribble \(name): \(r)% (at least \(least))\n"
      #expect(r >= least, "\(name) counts as a scribble only \(r)% of the time")
    }
    for (name, gen, most) in writing {
      let r = Self.rate(gen)
      report += "writing \(name): \(r)% (at most \(most))\n"
      #expect(r <= most, "\(name) counts as a scribble \(r)% of the time")
    }
    print(report)
  }
}
