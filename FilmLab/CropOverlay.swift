import SwiftUI

/// The crop box drawn over the photo. Must be placed exactly over the photo's frame (as an overlay).
struct CropOverlay: View {
    /// Normalized, origin at the top left.
    @Binding var rect: CGRect
    /// Normalized width/height ratio to keep, or nil for a free crop.
    let ratio: CGFloat?

    @State private var dragStart: CGRect?

    var body: some View {
        GeometryReader { geo in
            let size = geo.size
            let box = CGRect(
                x: rect.minX * size.width,
                y: rect.minY * size.height,
                width: rect.width * size.width,
                height: rect.height * size.height
            )
            ZStack {
                // Dim everything outside the box.
                Path { p in
                    p.addRect(CGRect(origin: .zero, size: size))
                    p.addRect(box)
                }
                .fill(Color.black.opacity(0.6), style: FillStyle(eoFill: true))

                thirds(in: box)
                    .stroke(Color.white.opacity(0.35), lineWidth: 0.5)
                Path { $0.addRect(box) }
                    .stroke(Color.white, lineWidth: 1.5)

                // Drag inside the box to move it.
                Color.white.opacity(0.001)
                    .frame(width: box.width, height: box.height)
                    .position(x: box.midX, y: box.midY)
                    .gesture(drag(in: size) { start, t in CropMath.move(start, by: t) })

                ForEach(0..<4, id: \.self) { corner in
                    Circle()
                        .fill(Color.white)
                        .frame(width: 14, height: 14)
                        .shadow(radius: 2)
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                        .position(point(of: corner, in: box))
                        .gesture(drag(in: size) { start, t in
                            CropMath.dragCorner(corner, of: start, by: t, ratio: ratio)
                        })
                }
            }
            .frame(width: size.width, height: size.height)
        }
    }

    private func drag(in size: CGSize, update: @escaping (CGRect, CGSize) -> CGRect) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                let start = dragStart ?? rect
                if dragStart == nil { dragStart = rect }
                let t = CGSize(width: value.translation.width / size.width, height: value.translation.height / size.height)
                rect = update(start, t)
            }
            .onEnded { _ in dragStart = nil }
    }

    private func point(of corner: Int, in box: CGRect) -> CGPoint {
        switch corner {
        case 0: CGPoint(x: box.minX, y: box.minY)
        case 1: CGPoint(x: box.maxX, y: box.minY)
        case 2: CGPoint(x: box.maxX, y: box.maxY)
        default: CGPoint(x: box.minX, y: box.maxY)
        }
    }

    private func thirds(in box: CGRect) -> Path {
        Path { p in
            for i in 1...2 {
                let x = box.minX + box.width * CGFloat(i) / 3
                p.move(to: CGPoint(x: x, y: box.minY))
                p.addLine(to: CGPoint(x: x, y: box.maxY))
                let y = box.minY + box.height * CGFloat(i) / 3
                p.move(to: CGPoint(x: box.minX, y: y))
                p.addLine(to: CGPoint(x: box.maxX, y: y))
            }
        }
    }
}
