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

  @Test func whereItBegins() {
    #expect(ZoomResist.free(content: nil, room: (1000, 700)) == 1)
    #expect(ZoomResist.free(content: Rect(0, 0, 100, 100), room: (1000, 700)) == 1)
    let f = ZoomResist.free(content: Rect(0, 0, 3952, 952), room: (1000, 700))
    #expect(abs(f - 0.25) < 0.001)
  }
}
