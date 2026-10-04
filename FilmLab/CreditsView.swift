import SwiftUI

/// The bundled looks are CC BY-SA 4.0, which requires visible credit and a link to the licence.
struct CreditsView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section("Looks") {
                    Text("""
                    Most looks are adapted from the RawTherapee Film Simulation Collection by Pat David, \
                    Pavlov Dmitry and Michael Ezra, and from the PictureFX CLUTs by Marc Roovers \
                    (Heat, Breeze, Sepia, Antique, Silver). They were converted from Hald CLUT images \
                    to 33-point .cube LUTs and renamed.
                    """)
                    Link("Licence: CC BY-SA 4.0", destination: URL(string: "https://creativecommons.org/licenses/by-sa/4.0/")!)
                    Link("RawTherapee Film Simulation", destination: URL(string: "https://rawpedia.rawtherapee.com/Film_Simulation")!)
                    Link("PictureFX", destination: URL(string: "http://www.digicrea.be")!)
                    Text("""
                    1975, Memory, Sunbleached, Expired, Tide and Disposable are FilmLab originals, \
                    dedicated to the public domain (CC0).
                    """)
                }
                Section {
                    Text("""
                    FilmLab is not affiliated with or endorsed by any film manufacturer. \
                    The adapted LUT files are shared under the same CC BY-SA 4.0 licence.
                    """)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Credits")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}
