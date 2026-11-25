import Foundation
import Testing

@testable import TaliCore

struct ZoomResistTests {
  @Test func downToWhereEverythingFitsItZoomsAsTheFingersSay() {
    for raw: Float in [3, 1.2, 1, 0.8, 0.5] { #expect(ZoomResist.resisted(raw, free: 0.5) == raw) }
  }

  @Test func justPastItItIsStillAlmostNormal() {
    // A tenth further out than where everything fits is taken almost whole.
    let r = ZoomResist.resisted(0.5 / 1.1, free: 0.5)
    #expect(r < 0.5 && r > 0.5 / 1.1 && r < 0.5 / 1.1 * 1.01)
  }

  @Test func theFartherOutTheHarderUntilItAlmostStops() {
    let free: Float = 0.5
    var last = free
    var step: Float = .greatestFiniteMagnitude
    // Each halving of the fingers' zoom moves it less than the one before, and it always goes on out.
    for k in 1...8 {
      let raw = free / powf(2, Float(k))
      let r = ZoomResist.resisted(raw, free: free)
      #expect(r < last && r > raw)
      let moved = logf(last / r)
      #expect(moved < step)
      step = moved
      last = r
    }
    // A very hard pinch stays within reach of where everything fits.
    #expect(ZoomResist.resisted(free / 1000, free: free) > free / ZoomResist.reach)
    #expect(step < 0.02)
  }

  @Test func aNewPinchCarriesOnFromWhereTheLastOneLeftIt() {
    for raw: Float in [0.45, 0.3, 0.2, 0.1, 0.05] {
      let shown = ZoomResist.resisted(raw, free: 0.5)
      #expect(abs(ZoomResist.raw(forShown: shown, free: 0.5) / raw - 1) < 0.001)
    }
    #expect(ZoomResist.raw(forShown: 0.8, free: 0.5) == 0.8)
  }

  @Test func aPinchStartingBeyondTheReachIsMeasuredFromThere() {
    #expect(abs(ZoomResist.freeAt(0.5, zoomNow: 0.1) - 0.1 * 2.5 / 1.01) < 1e-5)
    #expect(ZoomResist.freeAt(0.5, zoomNow: 0.3) == 0.5)
  }

  /// Android 1.0.34: pinching in follows the fingers from where the pinch began.
  @Test func pinchingInFollowsTheFingers() {
    let free: Float = 0.5
    let limit = free / ZoomResist.reach
    #expect(abs(ZoomResist.pinched(startZoom: limit, free: free, ratio: 2) - limit * 2) < 1e-5)
    #expect(ZoomResist.pinched(startZoom: limit, free: free, ratio: 0.1) >= limit * 0.999)
    #expect(abs(ZoomResist.pinched(startZoom: 0.3, free: free, ratio: 1) - 0.3) < 1e-4)
    #expect(abs(ZoomResist.pinched(startZoom: 0.8, free: free, ratio: 0.7) - 0.56) < 1e-5)
    // The cases the Android session gave: from 0.2, twice as wide shows 0.4; closing never goes below 0.2.
    #expect(abs(ZoomResist.pinched(startZoom: 0.2, free: free, ratio: 2) - 0.4) < 1e-5)
    #expect(ZoomResist.pinched(startZoom: 0.2, free: free, ratio: 0.1) >= 0.2 * 0.999)
  }

  @Test func whereItBegins() {
    #expect(ZoomResist.free(content: nil, room: (1000, 700)) == 1)
    #expect(ZoomResist.free(content: Rect(0, 0, 100, 100), room: (1000, 700)) == 1)
    let f = ZoomResist.free(content: Rect(0, 0, 3952, 952), room: (1000, 700))
    #expect(abs(f - 0.25) < 0.001)
  }
}
