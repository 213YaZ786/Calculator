package com.yaz.calculator.engine.programs

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/** Constants, CODATA 2018 (NIST, public domain). */
object Constants {
    const val G = 6.67430e-11
    const val C = 299_792_458.0
    const val H = 6.62607015e-34
    const val K = 8.9875517923e9
    const val R = 8.314462618
    const val NA = 6.02214076e23
    const val E = 1.602176634e-19
}

private fun w(fr: String, en: String) = Words(fr, en)
private fun f(symbol: String, fr: String, en: String, unit: String = "") = Field(symbol, Words(fr, en), unit)

/** The formulas of the physics programme (première and terminale), each solved for any of its quantities. */
val physics: List<Program> = listOf(
    Solver(
        "gravitation", Subject.PHYSICS, w("Attraction gravitationnelle", "Gravitational attraction"), "F = G·m₁·m₂ / d²",
        listOf(f("F", "Force", "Force", "N"), f("m₁", "Masse 1", "Mass 1", "kg"), f("m₂", "Masse 2", "Mass 2", "kg"), f("d", "Distance", "Distance", "m"))
    ) { v -> v["F"]!! - Constants.G * v["m₁"]!! * v["m₂"]!! / v["d"]!!.pow(2) },
    Solver(
        "weight", Subject.PHYSICS, w("Poids", "Weight"), "P = m·g",
        listOf(f("P", "Poids", "Weight", "N"), f("m", "Masse", "Mass", "kg"), f("g", "Intensité de la pesanteur", "Gravity", "N/kg"))
    ) { v -> v["P"]!! - v["m"]!! * v["g"]!! },
    Solver(
        "altitude", Subject.PHYSICS, w("Pesanteur en altitude", "Gravity at altitude"), "g = G·M / (R + h)²",
        listOf(f("g", "Pesanteur", "Gravity", "N/kg"), f("M", "Masse de l'astre", "Body's mass", "kg"), f("R", "Rayon de l'astre", "Body's radius", "m"), f("h", "Altitude", "Altitude", "m"))
    ) { v -> v["g"]!! - Constants.G * v["M"]!! / (v["R"]!! + v["h"]!!).pow(2) },
    Solver(
        "orbit", Subject.PHYSICS, w("Satellite en orbite circulaire", "Satellite on a circular orbit"), "v = √(G·M / r)",
        listOf(f("v", "Vitesse", "Speed", "m/s"), f("M", "Masse de l'astre", "Body's mass", "kg"), f("r", "Rayon de l'orbite", "Orbit radius", "m"))
    ) { v -> v["v"]!! - sqrt(Constants.G * v["M"]!! / v["r"]!!) },
    Solver(
        "kepler", Subject.PHYSICS, w("Troisième loi de Kepler", "Kepler's third law"), "T² / r³ = 4π² / (G·M)",
        listOf(f("T", "Période", "Period", "s"), f("r", "Rayon de l'orbite", "Orbit radius", "m"), f("M", "Masse de l'astre", "Body's mass", "kg"))
    ) { v -> v["T"]!!.pow(2) / v["r"]!!.pow(3) - 4 * PI * PI / (Constants.G * v["M"]!!) },
    Solver(
        "newton2", Subject.PHYSICS, w("Deuxième loi de Newton", "Newton's second law"), "F = m·a",
        listOf(f("F", "Force", "Force", "N"), f("m", "Masse", "Mass", "kg"), f("a", "Accélération", "Acceleration", "m/s²")), positive = false
    ) { v -> v["F"]!! - v["m"]!! * v["a"]!! },
    Solver(
        "uniform", Subject.PHYSICS, w("Mouvement uniformément accéléré", "Uniformly accelerated motion"), "x = v₀·t + ½·a·t²",
        listOf(f("x", "Distance parcourue", "Distance", "m"), f("v₀", "Vitesse initiale", "Initial speed", "m/s"), f("t", "Durée", "Time", "s"), f("a", "Accélération", "Acceleration", "m/s²")), positive = false
    ) { v -> v["x"]!! - (v["v₀"]!! * v["t"]!! + 0.5 * v["a"]!! * v["t"]!!.pow(2)) },
    Solver(
        "speed", Subject.PHYSICS, w("Vitesse après accélération", "Speed after accelerating"), "v = v₀ + a·t",
        listOf(f("v", "Vitesse", "Speed", "m/s"), f("v₀", "Vitesse initiale", "Initial speed", "m/s"), f("a", "Accélération", "Acceleration", "m/s²"), f("t", "Durée", "Time", "s")), positive = false
    ) { v -> v["v"]!! - (v["v₀"]!! + v["a"]!! * v["t"]!!) },
    Solver(
        "kinetic", Subject.PHYSICS, w("Énergie cinétique", "Kinetic energy"), "Ec = ½·m·v²",
        listOf(f("Ec", "Énergie cinétique", "Kinetic energy", "J"), f("m", "Masse", "Mass", "kg"), f("v", "Vitesse", "Speed", "m/s"))
    ) { v -> v["Ec"]!! - 0.5 * v["m"]!! * v["v"]!!.pow(2) },
    Solver(
        "potential", Subject.PHYSICS, w("Énergie potentielle de pesanteur", "Gravitational potential energy"), "Epp = m·g·h",
        listOf(f("Epp", "Énergie potentielle", "Potential energy", "J"), f("m", "Masse", "Mass", "kg"), f("g", "Pesanteur", "Gravity", "N/kg"), f("h", "Hauteur", "Height", "m")), positive = false
    ) { v -> v["Epp"]!! - v["m"]!! * v["g"]!! * v["h"]!! },
    Solver(
        "fall", Subject.PHYSICS, w("Chute libre : vitesse à l'arrivée", "Free fall: landing speed"), "½·m·v² = m·g·h  ⇒  v = √(2·g·h)",
        listOf(f("v", "Vitesse", "Speed", "m/s"), f("g", "Pesanteur", "Gravity", "N/kg"), f("h", "Hauteur de chute", "Height", "m"))
    ) { v -> v["v"]!! - sqrt(2 * v["g"]!! * v["h"]!!) },
    Solver(
        "power", Subject.PHYSICS, w("Puissance et énergie", "Power and energy"), "E = P·Δt",
        listOf(f("E", "Énergie", "Energy", "J"), f("P", "Puissance", "Power", "W"), f("Δt", "Durée", "Time", "s"))
    ) { v -> v["E"]!! - v["P"]!! * v["Δt"]!! },
    Solver(
        "coulomb", Subject.PHYSICS, w("Force électrostatique (Coulomb)", "Electrostatic force (Coulomb)"), "F = k·|q₁·q₂| / d²",
        listOf(f("F", "Force", "Force", "N"), f("q₁", "Charge 1", "Charge 1", "C"), f("q₂", "Charge 2", "Charge 2", "C"), f("d", "Distance", "Distance", "m"))
    ) { v -> v["F"]!! - Constants.K * Math.abs(v["q₁"]!! * v["q₂"]!!) / v["d"]!!.pow(2) },
    Solver(
        "ohm", Subject.PHYSICS, w("Loi d'Ohm", "Ohm's law"), "U = R·I",
        listOf(f("U", "Tension", "Voltage", "V"), f("R", "Résistance", "Resistance", "Ω"), f("I", "Intensité", "Current", "A"))
    ) { v -> v["U"]!! - v["R"]!! * v["I"]!! },
    Solver(
        "electric", Subject.PHYSICS, w("Puissance électrique", "Electric power"), "P = U·I",
        listOf(f("P", "Puissance", "Power", "W"), f("U", "Tension", "Voltage", "V"), f("I", "Intensité", "Current", "A"))
    ) { v -> v["P"]!! - v["U"]!! * v["I"]!! },
    Solver(
        "joule", Subject.PHYSICS, w("Effet Joule", "Joule heating"), "P = R·I²",
        listOf(f("P", "Puissance", "Power", "W"), f("R", "Résistance", "Resistance", "Ω"), f("I", "Intensité", "Current", "A"))
    ) { v -> v["P"]!! - v["R"]!! * v["I"]!!.pow(2) },
    Solver(
        "rc", Subject.PHYSICS, w("Charge d'un condensateur (RC)", "Capacitor charging (RC)"), "u = E·(1 − e^(−t / (R·C)))",
        listOf(f("u", "Tension aux bornes", "Voltage", "V"), f("E", "Tension du générateur", "Source voltage", "V"), f("t", "Instant", "Time", "s"), f("R", "Résistance", "Resistance", "Ω"), f("C", "Capacité", "Capacitance", "F"))
    ) { v -> v["u"]!! - v["E"]!! * (1 - exp(-v["t"]!! / (v["R"]!! * v["C"]!!))) },
    Solver(
        "capacitor", Subject.PHYSICS, w("Charge d'un condensateur", "Charge of a capacitor"), "Q = C·U",
        listOf(f("Q", "Charge", "Charge", "C"), f("C", "Capacité", "Capacitance", "F"), f("U", "Tension", "Voltage", "V"))
    ) { v -> v["Q"]!! - v["C"]!! * v["U"]!! },
    Solver(
        "wave", Subject.PHYSICS, w("Onde périodique", "Periodic wave"), "v = λ·f",
        listOf(f("v", "Célérité", "Speed", "m/s"), f("λ", "Longueur d'onde", "Wavelength", "m"), f("f", "Fréquence", "Frequency", "Hz"))
    ) { v -> v["v"]!! - v["λ"]!! * v["f"]!! },
    Solver(
        "diffraction", Subject.PHYSICS, w("Diffraction", "Diffraction"), "θ = λ / a",
        listOf(f("θ", "Écart angulaire", "Angle", "rad"), f("λ", "Longueur d'onde", "Wavelength", "m"), f("a", "Largeur de la fente", "Slit width", "m"))
    ) { v -> v["θ"]!! - v["λ"]!! / v["a"]!! },
    Solver(
        "interference", Subject.PHYSICS, w("Interférences (fentes d'Young)", "Interference (Young's slits)"), "i = λ·D / b",
        listOf(f("i", "Interfrange", "Fringe spacing", "m"), f("λ", "Longueur d'onde", "Wavelength", "m"), f("D", "Distance à l'écran", "Screen distance", "m"), f("b", "Écart des fentes", "Slit spacing", "m"))
    ) { v -> v["i"]!! - v["λ"]!! * v["D"]!! / v["b"]!! },
    Solver(
        "doppler", Subject.PHYSICS, w("Effet Doppler (source qui approche)", "Doppler effect (source approaching)"), "f' = f · v / (v − vs)",
        listOf(f("f'", "Fréquence perçue", "Heard frequency", "Hz"), f("f", "Fréquence émise", "Emitted frequency", "Hz"), f("v", "Célérité de l'onde", "Wave speed", "m/s"), f("vs", "Vitesse de la source", "Source speed", "m/s"))
    ) { v -> v["f'"]!! - v["f"]!! * v["v"]!! / (v["v"]!! - v["vs"]!!) },
    Solver(
        "lens", Subject.PHYSICS, w("Lentille mince (conjugaison)", "Thin lens"), "1/OA' − 1/OA = 1/f'",
        listOf(f("OA'", "Position de l'image", "Image position", "m"), f("OA", "Position de l'objet", "Object position", "m"), f("f'", "Distance focale", "Focal length", "m")), positive = false
    ) { v -> 1 / v["OA'"]!! - 1 / v["OA"]!! - 1 / v["f'"]!! },
    Solver(
        "photon", Subject.PHYSICS, w("Énergie d'un photon", "Photon energy"), "E = h·c / λ",
        listOf(f("E", "Énergie", "Energy", "J"), f("λ", "Longueur d'onde", "Wavelength", "m"))
    ) { v -> v["E"]!! - Constants.H * Constants.C / v["λ"]!! },
    Solver(
        "heat", Subject.PHYSICS, w("Énergie thermique", "Heat"), "Q = m·c·ΔT",
        listOf(f("Q", "Énergie", "Heat", "J"), f("m", "Masse", "Mass", "kg"), f("c", "Capacité thermique massique", "Specific heat", "J/(kg·K)"), f("ΔT", "Variation de température", "Temperature change", "K")), positive = false
    ) { v -> v["Q"]!! - v["m"]!! * v["c"]!! * v["ΔT"]!! },
    Solver(
        "gas", Subject.PHYSICS, w("Gaz parfait", "Ideal gas"), "P·V = n·R·T",
        listOf(f("P", "Pression", "Pressure", "Pa"), f("V", "Volume", "Volume", "m³"), f("n", "Quantité de matière", "Amount", "mol"), f("T", "Température", "Temperature", "K"))
    ) { v -> v["P"]!! * v["V"]!! - v["n"]!! * Constants.R * v["T"]!! },
    Solver(
        "hydrostatic", Subject.PHYSICS, w("Pression dans un liquide", "Pressure in a liquid"), "P = P₀ + ρ·g·h",
        listOf(f("P", "Pression", "Pressure", "Pa"), f("P₀", "Pression en surface", "Surface pressure", "Pa"), f("ρ", "Masse volumique", "Density", "kg/m³"), f("g", "Pesanteur", "Gravity", "N/kg"), f("h", "Profondeur", "Depth", "m"))
    ) { v -> v["P"]!! - (v["P₀"]!! + v["ρ"]!! * v["g"]!! * v["h"]!!) },
    Solver(
        "decay", Subject.PHYSICS, w("Décroissance radioactive", "Radioactive decay"), "N = N₀·e^(−ln2·t / t½)",
        listOf(f("N", "Noyaux restants", "Nuclei left"), f("N₀", "Noyaux au départ", "Nuclei at start"), f("t", "Durée", "Time", "s"), f("t½", "Demi-vie", "Half-life", "s"))
    ) { v -> v["N"]!! - v["N₀"]!! * exp(-ln(2.0) * v["t"]!! / v["t½"]!!) }
)

/** The chemistry of the programme, each relation solved for any of its quantities. */
val chemistry: List<Program> = listOf(
    Solver(
        "moles", Subject.CHEMISTRY, w("Quantité de matière", "Amount of substance"), "n = m / M",
        listOf(f("n", "Quantité de matière", "Amount", "mol"), f("m", "Masse", "Mass", "g"), f("M", "Masse molaire", "Molar mass", "g/mol"))
    ) { v -> v["n"]!! - v["m"]!! / v["M"]!! },
    Solver(
        "concentration", Subject.CHEMISTRY, w("Concentration", "Concentration"), "c = n / V",
        listOf(f("c", "Concentration", "Concentration", "mol/L"), f("n", "Quantité de matière", "Amount", "mol"), f("V", "Volume", "Volume", "L"))
    ) { v -> v["c"]!! - v["n"]!! / v["V"]!! },
    Solver(
        "dilution", Subject.CHEMISTRY, w("Dilution", "Dilution"), "c₁·V₁ = c₂·V₂",
        listOf(f("c₁", "Concentration mère", "Stock concentration", "mol/L"), f("V₁", "Volume prélevé", "Volume taken", "L"), f("c₂", "Concentration fille", "Diluted concentration", "mol/L"), f("V₂", "Volume final", "Final volume", "L"))
    ) { v -> v["c₁"]!! * v["V₁"]!! - v["c₂"]!! * v["V₂"]!! },
    Solver(
        "ph", Subject.CHEMISTRY, w("pH d'une solution", "pH of a solution"), "pH = −log[H₃O⁺]",
        listOf(f("pH", "pH", "pH"), f("[H₃O⁺]", "Concentration en ions oxonium", "Hydronium concentration", "mol/L")), positive = false
    ) { v -> v["pH"]!! + log10(v["[H₃O⁺]"]!!) },
    Solver(
        "buffer", Subject.CHEMISTRY, w("pH et pKa (Henderson)", "pH and pKa (Henderson)"), "pH = pKa + log([A⁻] / [AH])",
        listOf(f("pH", "pH", "pH"), f("pKa", "pKa", "pKa"), f("[A⁻]", "Base", "Base", "mol/L"), f("[AH]", "Acide", "Acid", "mol/L")), positive = false
    ) { v -> v["pH"]!! - (v["pKa"]!! + log10(v["[A⁻]"]!! / v["[AH]"]!!)) },
    Solver(
        "beer", Subject.CHEMISTRY, w("Loi de Beer-Lambert", "Beer-Lambert law"), "A = ε·ℓ·c",
        listOf(f("A", "Absorbance", "Absorbance"), f("ε", "Coefficient d'absorption molaire", "Molar absorptivity", "L/(mol·cm)"), f("ℓ", "Longueur de cuve", "Path length", "cm"), f("c", "Concentration", "Concentration", "mol/L"))
    ) { v -> v["A"]!! - v["ε"]!! * v["ℓ"]!! * v["c"]!! },
    Solver(
        "titration", Subject.CHEMISTRY, w("Titrage : équivalence", "Titration: equivalence"), "cₐ·Vₐ = c_b·V_E",
        listOf(f("cₐ", "Concentration titrée", "Titrated concentration", "mol/L"), f("Vₐ", "Volume titré", "Titrated volume", "mL"), f("c_b", "Concentration titrante", "Titrant concentration", "mol/L"), f("V_E", "Volume à l'équivalence", "Equivalence volume", "mL"))
    ) { v -> v["cₐ"]!! * v["Vₐ"]!! - v["c_b"]!! * v["V_E"]!! },
    Solver(
        "kinetics", Subject.CHEMISTRY, w("Cinétique d'ordre 1", "First-order kinetics"), "[A] = [A]₀·e^(−k·t)",
        listOf(f("[A]", "Concentration", "Concentration", "mol/L"), f("[A]₀", "Concentration initiale", "Initial concentration", "mol/L"), f("k", "Constante de vitesse", "Rate constant", "s⁻¹"), f("t", "Instant", "Time", "s"))
    ) { v -> v["[A]"]!! - v["[A]₀"]!! * exp(-v["k"]!! * v["t"]!!) },
    Solver(
        "halflife", Subject.CHEMISTRY, w("Temps de demi-réaction (ordre 1)", "Half-life (first order)"), "t½ = ln2 / k",
        listOf(f("t½", "Temps de demi-réaction", "Half-life", "s"), f("k", "Constante de vitesse", "Rate constant", "s⁻¹"))
    ) { v -> v["t½"]!! - ln(2.0) / v["k"]!! },
    Solver(
        "density", Subject.CHEMISTRY, w("Masse volumique", "Density"), "ρ = m / V",
        listOf(f("ρ", "Masse volumique", "Density", "g/mL"), f("m", "Masse", "Mass", "g"), f("V", "Volume", "Volume", "mL"))
    ) { v -> v["ρ"]!! - v["m"]!! / v["V"]!! },
    Solver(
        "avogadro", Subject.CHEMISTRY, w("Nombre d'entités", "Number of entities"), "N = n·Nₐ",
        listOf(f("N", "Nombre d'entités", "Number of entities"), f("n", "Quantité de matière", "Amount", "mol"))
    ) { v -> v["N"]!! - v["n"]!! * Constants.NA }
)
