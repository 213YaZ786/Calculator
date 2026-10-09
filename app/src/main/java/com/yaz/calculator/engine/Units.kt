package com.yaz.calculator.engine

import java.util.Locale

/** What can be converted. Currency has its own rates, fetched; the rest is exact here. */
enum class Category { CURRENCY, LENGTH, AREA, VOLUME, MASS, TEMPERATURE, SPEED, TIME, DATA, PRESSURE, ENERGY, POWER, FUEL, ANGLE }

/**
 * One unit: how a value in it becomes the category's base unit and back.
 * Factors are exact (an inch is 0.0254 m by definition), so converting
 * back and forth gives the number typed.
 */
class MeasureUnit(
    val id: String,
    val name: String,
    val symbol: String,
    val toBase: (UnifiedReal) -> UnifiedReal,
    val fromBase: (UnifiedReal) -> UnifiedReal
)

object Units {

    /** A decimal or a fraction written plainly (0.0254, 5/18, 1.6e-19), exactly. */
    fun exact(text: String): UnifiedReal {
        val parts = text.split('/')
        val top = Calc.literal(parts[0].replace("e-", "E${Sym.MINUS}").replace("e", "E"))
        return if (parts.size == 2) top.divide(Calc.literal(parts[1])) else top
    }

    private fun linear(id: String, name: String, symbol: String, factor: String) =
        linear(id, name, symbol, exact(factor))

    private fun linear(id: String, name: String, symbol: String, factor: UnifiedReal) =
        MeasureUnit(id, name, symbol, { it.multiply(factor) }, { it.divide(factor) })

    /** A unit that is the inverse of the base: km/L against L/100 km. */
    private fun inverse(id: String, name: String, symbol: String, product: String): MeasureUnit {
        val k = exact(product)
        return MeasureUnit(id, name, symbol, { k.divide(it) }, { k.divide(it) })
    }

    private val C_TO_K = exact("273.15")
    private val NINE_FIFTHS = exact("9/5")
    private val F_ZERO = exact("32")
    private val HALF_TURN = UnifiedReal(180)

    val all: Map<Category, List<MeasureUnit>> by lazy {
        mapOf(
            Category.LENGTH to listOf(
                linear("nm", "Nanometre", "nm", "1e-9"),
                linear("um", "Micrometre", "µm", "1e-6"),
                linear("mm", "Millimetre", "mm", "0.001"),
                linear("cm", "Centimetre", "cm", "0.01"),
                linear("m", "Metre", "m", "1"),
                linear("km", "Kilometre", "km", "1000"),
                linear("in", "Inch", "in", "0.0254"),
                linear("ft", "Foot", "ft", "0.3048"),
                linear("yd", "Yard", "yd", "0.9144"),
                linear("mi", "Mile", "mi", "1609.344"),
                linear("nmi", "Nautical mile", "nmi", "1852"),
                linear("ly", "Light year", "ly", "9460730472580800")
            ),
            Category.AREA to listOf(
                linear("mm2", "Square millimetre", "mm²", "1e-6"),
                linear("cm2", "Square centimetre", "cm²", "0.0001"),
                linear("m2", "Square metre", "m²", "1"),
                linear("a", "Are", "a", "100"),
                linear("ha", "Hectare", "ha", "10000"),
                linear("km2", "Square kilometre", "km²", "1000000"),
                linear("in2", "Square inch", "in²", "0.00064516"),
                linear("ft2", "Square foot", "ft²", "0.09290304"),
                linear("yd2", "Square yard", "yd²", "0.83612736"),
                linear("ac", "Acre", "ac", "4046.8564224"),
                linear("mi2", "Square mile", "mi²", "2589988.110336")
            ),
            Category.VOLUME to listOf(
                linear("ml", "Millilitre", "mL", "0.001"),
                linear("cl", "Centilitre", "cL", "0.01"),
                linear("dl", "Decilitre", "dL", "0.1"),
                linear("l", "Litre", "L", "1"),
                linear("m3", "Cubic metre", "m³", "1000"),
                linear("cm3", "Cubic centimetre", "cm³", "0.001"),
                linear("tsp", "Teaspoon (US)", "tsp", "0.00492892159375"),
                linear("tbsp", "Tablespoon (US)", "tbsp", "0.01478676478125"),
                linear("floz", "Fluid ounce (US)", "fl oz", "0.0295735295625"),
                linear("cup", "Cup (US)", "cup", "0.2365882365"),
                linear("pt", "Pint (US)", "pt", "0.473176473"),
                linear("qt", "Quart (US)", "qt", "0.946352946"),
                linear("gal", "Gallon (US)", "gal", "3.785411784"),
                linear("flozuk", "Fluid ounce (UK)", "fl oz", "0.0284130625"),
                linear("ptuk", "Pint (UK)", "pt", "0.56826125"),
                linear("galuk", "Gallon (UK)", "gal", "4.54609"),
                linear("in3", "Cubic inch", "in³", "0.016387064"),
                linear("ft3", "Cubic foot", "ft³", "28.316846592")
            ),
            Category.MASS to listOf(
                linear("mg", "Milligram", "mg", "1e-6"),
                linear("g", "Gram", "g", "0.001"),
                linear("kg", "Kilogram", "kg", "1"),
                linear("t", "Tonne", "t", "1000"),
                linear("ct", "Carat", "ct", "0.0002"),
                linear("oz", "Ounce", "oz", "0.028349523125"),
                linear("lb", "Pound", "lb", "0.45359237"),
                linear("st", "Stone", "st", "6.35029318"),
                linear("ton", "Short ton (US)", "ton", "907.18474")
            ),
            Category.TEMPERATURE to listOf(
                MeasureUnit("c", "Celsius", "°C", { it }, { it }),
                MeasureUnit("f", "Fahrenheit", "°F", { it.subtract(F_ZERO).divide(NINE_FIFTHS) }, { it.multiply(NINE_FIFTHS).add(F_ZERO) }),
                MeasureUnit("k", "Kelvin", "K", { it.subtract(C_TO_K) }, { it.add(C_TO_K) })
            ),
            Category.SPEED to listOf(
                linear("mps", "Metre per second", "m/s", "1"),
                linear("kmh", "Kilometre per hour", "km/h", "5/18"),
                linear("mph", "Mile per hour", "mph", "0.44704"),
                linear("kn", "Knot", "kn", "463/900"),
                linear("fps", "Foot per second", "ft/s", "0.3048")
            ),
            Category.TIME to listOf(
                linear("ms", "Millisecond", "ms", "0.001"),
                linear("s", "Second", "s", "1"),
                linear("min", "Minute", "min", "60"),
                linear("h", "Hour", "h", "3600"),
                linear("d", "Day", "d", "86400"),
                linear("wk", "Week", "wk", "604800"),
                linear("mo", "Month", "mo", "2629746"),
                linear("yr", "Year", "yr", "31556952")
            ),
            Category.DATA to listOf(
                linear("bit", "Bit", "bit", "1/8"),
                linear("B", "Byte", "B", "1"),
                linear("kB", "Kilobyte", "kB", "1000"),
                linear("MB", "Megabyte", "MB", "1000000"),
                linear("GB", "Gigabyte", "GB", "1000000000"),
                linear("TB", "Terabyte", "TB", "1000000000000"),
                linear("KiB", "Kibibyte", "KiB", "1024"),
                linear("MiB", "Mebibyte", "MiB", "1048576"),
                linear("GiB", "Gibibyte", "GiB", "1073741824"),
                linear("TiB", "Tebibyte", "TiB", "1099511627776")
            ),
            Category.PRESSURE to listOf(
                linear("pa", "Pascal", "Pa", "1"),
                linear("hpa", "Hectopascal", "hPa", "100"),
                linear("kpa", "Kilopascal", "kPa", "1000"),
                linear("mbar", "Millibar", "mbar", "100"),
                linear("bar", "Bar", "bar", "100000"),
                linear("atm", "Atmosphere", "atm", "101325"),
                // A pound-force per square inch: 0.45359237 kg × 9.80665 m/s² over (0.0254 m)².
                linear("psi", "Pound per square inch", "psi", exact("0.45359237").multiply(exact("9.80665")).divide(exact("0.00064516"))),
                linear("mmhg", "Millimetre of mercury", "mmHg", "133.322387415"),
                linear("inhg", "Inch of mercury", "inHg", "3386.389")
            ),
            Category.ENERGY to listOf(
                linear("j", "Joule", "J", "1"),
                linear("kj", "Kilojoule", "kJ", "1000"),
                linear("cal", "Calorie", "cal", "4.184"),
                linear("kcal", "Kilocalorie", "kcal", "4184"),
                linear("wh", "Watt hour", "Wh", "3600"),
                linear("kwh", "Kilowatt hour", "kWh", "3600000"),
                linear("btu", "British thermal unit", "BTU", "1055.05585262"),
                linear("ev", "Electronvolt", "eV", "1.602176634e-19")
            ),
            Category.POWER to listOf(
                linear("w", "Watt", "W", "1"),
                linear("kw", "Kilowatt", "kW", "1000"),
                linear("mw", "Megawatt", "MW", "1000000"),
                linear("hp", "Horsepower", "hp", "745.69987158227022"),
                linear("ps", "Metric horsepower", "PS", "735.49875"),
                linear("btuh", "BTU per hour", "BTU/h", "0.29307107017")
            ),
            Category.FUEL to listOf(
                MeasureUnit("l100", "Litres per 100 km", "L/100 km", { it }, { it }),
                inverse("kml", "Kilometres per litre", "km/L", "100"),
                // 100 km in miles, over a gallon in litres.
                inverse("mpg", "Miles per gallon (US)", "mpg", "378.5411784/1.609344"),
                inverse("mpguk", "Miles per gallon (UK)", "mpg", "454.609/1.609344")
            ),
            Category.ANGLE to listOf(
                linear("deg", "Degree", "°", "1"),
                // Through π itself, so π rad is exactly 180°.
                MeasureUnit("rad", "Radian", "rad", { it.multiply(HALF_TURN).divide(UnifiedReal.PI) }, { it.multiply(UnifiedReal.PI).divide(HALF_TURN) }),
                linear("grad", "Gradian", "gon", "0.9"),
                linear("arcmin", "Minute of arc", "′", "1/60"),
                linear("arcsec", "Second of arc", "″", "1/3600"),
                linear("turn", "Turn", "tr", "360")
            )
        )
    }

    fun find(category: Category, id: String): MeasureUnit? = all[category]?.firstOrNull { it.id == id }

    /** Countries measuring in miles, pounds and Fahrenheit, then the United Kingdom's own mix. */
    private val IMPERIAL = setOf("US", "LR", "MM")

    /** The pair a person of [locale] most likely converts first: their own unit, then the other system's. */
    fun defaults(category: Category, locale: Locale): Pair<String, String> {
        val country = locale.country
        val us = country in IMPERIAL
        val uk = country == "GB"
        return when (category) {
            Category.CURRENCY -> "" to ""
            Category.LENGTH -> if (us) "mi" to "km" else "km" to "mi"
            Category.AREA -> if (us) "ft2" to "m2" else "m2" to "ft2"
            Category.VOLUME -> when { us -> "gal" to "l"; uk -> "ptuk" to "l"; else -> "l" to "gal" }
            Category.MASS -> when { us -> "lb" to "kg"; uk -> "st" to "kg"; else -> "kg" to "lb" }
            Category.TEMPERATURE -> if (us) "f" to "c" else "c" to "f"
            Category.SPEED -> if (us || uk) "mph" to "kmh" else "kmh" to "mph"
            Category.TIME -> "h" to "min"
            Category.DATA -> "GB" to "MB"
            Category.PRESSURE -> if (us) "psi" to "bar" else "bar" to "psi"
            Category.ENERGY -> "kcal" to "kj"
            Category.POWER -> if (us || uk) "hp" to "kw" else "kw" to "hp"
            Category.FUEL -> when { us -> "mpg" to "l100"; uk -> "mpguk" to "l100"; else -> "l100" to "mpg" }
            Category.ANGLE -> "deg" to "rad"
        }
    }
}
