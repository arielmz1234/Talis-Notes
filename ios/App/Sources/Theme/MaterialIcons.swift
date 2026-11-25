import SwiftUI

/// Icons the Android app takes from Material's icon set, drawn from the same path data on their
/// 24-unit grid (filled, as Material draws them).
enum MaterialIcon {
  /// `Icons.Outlined.FlipToFront`: a solid square over a dotted one (Bring to front).
  case flipToFront
  /// `Icons.Outlined.FlipToBack`: a dotted square over a solid corner (Send to back).
  case flipToBack

  var pathData: String {
    switch self {
    case .flipToFront:
      "M3 13h2v-2H3v2zm0 4h2v-2H3v2zm2 4v-2H3c0 1.1.89 2 2 2zM3 9h2V7H3v2zm12 12h2v-2h-2v2zm4-18H9c-1.11 0-2 .9-2 2v10c0 1.1.89 2 2 2h10c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 12H9V5h10v10zm-8 6h2v-2h-2v2zm-4 0h2v-2H7v2z"
    case .flipToBack:
      "M9 7H7v2h2V7zm0 4H7v2h2v-2zm0-8c-1.11 0-2 .9-2 2h2V3zm4 12h-2v2h2v-2zm6-12v2h2c0-1.1-.9-2-2-2zm-6 0h-2v2h2V3zM9 17v-2H7c0 1.1.89 2 2 2zm10-4h2v-2h-2v2zm0-4h2V7h-2v2zm0 8c1.1 0 2-.9 2-2h-2v2zM5 7H3v12c0 1.1.89 2 2 2h12v-2H5V7zm10-2h2V3h-2v2zm0 12h2v-2h-2v2z"
    }
  }

}

/// The icon drawn [size] points square in [color] (path data read by `SVGPath`, Folders.swift).
struct MaterialIconView: View {
  var icon: MaterialIcon
  var color: Color
  var size: CGFloat = 22

  var body: some View {
    MaterialIconShape(data: icon.pathData).fill(color, style: FillStyle(eoFill: true)).frame(width: size, height: size)
  }
}

private struct MaterialIconShape: Shape {
  var data: String

  func path(in rect: CGRect) -> Path {
    SVGPath.parse(data).applying(CGAffineTransform(scaleX: rect.width / 24, y: rect.height / 24).translatedBy(x: rect.minX, y: rect.minY))
  }
}
