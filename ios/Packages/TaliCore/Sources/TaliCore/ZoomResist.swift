import Foundation

/// Zooming out on an endless canvas (the owner, 25 Nov 2026): no hard limit, but the farther out
/// past what's written, the harder it gets, until it almost stops. So a hard pinch by accident
/// can't leave her so far out that her notes are lost specks on a sheet many screens wide.
///
/// [free] is the zoom at which everything written fits on screen (never more than 1): down to it, a
/// pinch zooms exactly as the fingers say. Past it, each further step out counts for less, and
/// however far she pinches it never goes beyond [reach] times further out than [free]. In steps
/// (logarithms) of zoom: x = ln(free / raw) is how far the fingers asked to go past it, and the
/// zoom goes y = L·(1 − e^(−x/L)) past it, with L = ln(reach). Near [free] the two are the same
/// (y ≈ x, so it starts out normal); the farther out, the less of each pinch is taken.
public enum ZoomResist {
  /// How many times further out than [free] zooming can ever go.
  public static let reach: Float = 2.5

  /// The zoom to show when the fingers ask for [raw].
  public static func resisted(_ raw: Float, free: Float) -> Float {
    guard raw > 0, free > 0, raw < free else { return raw }
    let l = logf(reach)
    let x = logf(free / raw)
    let y = l * (1 - expf(-x / l))
    return free / expf(y)
  }

  /// The zoom the fingers would have to ask for to show [shown] (the other way round): where a new
  /// pinch starts from when she's already zoomed out past [free], so it carries on without a jump.
  public static func raw(forShown shown: Float, free: Float) -> Float {
    guard shown > 0, free > 0, shown < free else { return shown }
    let l = logf(reach)
    let y = min(logf(free / shown), l * 0.999)
    return free / expf(-l * logf(1 - y / l))
  }

  /// The zoom for a pinch that started at [startZoom] with the fingers now [ratio] times as far
  /// apart (Android 1.0.34's `ZoomResist.pinched`). Out (ratio < 1) it carries on along the
  /// resistance with no jump; in, it follows the fingers from where it started. (Following the
  /// curve back in too, a pinch started near the farthest zoom barely moved: the fingers first had
  /// to undo all the resisted distance; spreading them five times wider still showed 40%.)
  public static func pinched(startZoom: Float, free: Float, ratio: Float) -> Float {
    ratio >= 1 ? startZoom * ratio : resisted(raw(forShown: startZoom, free: free) * ratio, free: free)
  }

  /// [free] for a pinch starting at [zoomNow]: when that's already beyond its reach (the writing
  /// grew, the screen turned), measured from there (Android's `freeAt`).
  public static func freeAt(_ free: Float, zoomNow: Float) -> Float {
    zoomNow < free / reach ? zoomNow * reach / 1.01 : free
  }

  /// Where resistance begins: the zoom that fits [content] (world units, with a margin) into
  /// [room] (screen points), never more than 1; with nothing written, 1.
  public static func free(content: Rect?, room: (width: Float, height: Float)) -> Float {
    guard let c = content, c.width > 1 || c.height > 1, room.width > 0, room.height > 0 else { return 1 }
    let w = c.width + 48, h = c.height + 48
    return min(min(room.width / w, room.height / h), 1)
  }
}
