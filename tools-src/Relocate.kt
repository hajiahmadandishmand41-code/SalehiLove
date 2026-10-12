import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper
import java.io.File
import java.util.jar.JarFile
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Relocates packages inside a jar (ASM ClassRemapper).
 *
 * Usage: Relocate in.jar out.jar from1 to1 [from2 to2 ...] [-exclude prefix]*
 *
 * - exclude prefixes drop classes/resources entirely (e.g. bundled guava).
 */
object Relocate {

    class PrefixRemapper(val rules: List<Pair<String, String>>) : Remapper() {
        override fun map(internalName: String): String {
            var name = internalName
            for ((from, to) in rules) {
                if (name == from || name.startsWith("$from/")) {
                    return "$to" + name.removePrefix(from)
                }
            }
            return name
        }

        override fun mapPackageName(name: String): String {
            for ((from, to) in rules) {
                if (name == from || name.startsWith("$from/")) {
                    return "$to" + name.removePrefix(from)
                }
            }
            return name
        }

        override fun mapValue(value: Any?): Any? {
            if (value is String) {
                @Suppress("UNCHECKED_CAST")
                var s: String = value as String
                // mapString handles both internal (a/b/C) and dotted (a.b.C) forms.
                for ((from, to) in rules) {
                    val fromD = from.replace('/', '.')
                    val toD = to.replace('/', '.')
                    s = s.replace("$fromD.", "$toD.")
                        .replace("$fromD\"", "$toD\"")
                    if (s == from || s.startsWith("$from/")) s = "$to" + s.removePrefix(from)
                    if (s == fromD) s = toD
                }
                return s
            }
            return super.mapValue(value)
        }
    }

    fun mapResourceName(name: String, remapper: PrefixRemapper, excludes: List<String>): String? {
        if (excludes.any { name.startsWith(it) }) return null
        return remapper.mapPackageName(name)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val inJar = args[0]
        val outJar = args[1]
        val rules = mutableListOf<Pair<String, String>>()
        val excludes = mutableListOf<String>()
        var i = 2
        while (i < args.size) {
            if (args[i] == "-exclude") {
                excludes.add(args[i + 1]); i += 2
            } else {
                rules.add(args[i] to args[i + 1]); i += 2
            }
        }
        println("rules=$rules excludes=$excludes")
        val remapper = PrefixRemapper(rules)
        val zin = ZipFile(File(inJar))
        val out = ZipOutputStream(File(outJar).outputStream().buffered())
        var copied = 0; var remapped = 0; var excluded = 0
        for (entry in zin.entries()) {
            val name = entry.name
            if (excludes.any { name.startsWith(it) }) {
                excluded++; continue
            }
            val newName = mapResourceName(name, remapper, emptyList())
            if (name.endsWith(".class")) {
                val bytes = zin.getInputStream(entry).readBytes()
                val reader = ClassReader(bytes)
                val writer = ClassWriter(reader, 0)
                reader.accept(ClassRemapper(writer, remapper), 0)
                val outBytes = writer.toByteArray()
                out.putNextEntry(ZipEntry(newName))
                out.write(outBytes)
                out.closeEntry()
                remapped++
            } else {
                out.putNextEntry(ZipEntry(newName))
                zin.getInputStream(entry).copyTo(out)
                out.closeEntry()
                copied++
            }
        }
        out.close()
        zin.close()
        println("done: remapped=$remapped copied=$copied excluded=$excluded -> $outJar")
    }
}

/**
 * Merges jars (first-wins for duplicate entries).
 * Usage: Jars out.jar in1.jar in2.jar ...
 */
object Jars {
    @JvmStatic
    fun main(args: Array<String>) {
        val outJar = args[0]
        val seen = HashSet<String>()
        val out = ZipOutputStream(File(outJar).outputStream().buffered())
        var total = 0
        for (path in args.drop(1)) {
            val zin = ZipFile(File(path))
            for (entry in zin.entries()) {
                if (entry.isDirectory) continue
                if (!seen.add(entry.name)) continue
                out.putNextEntry(ZipEntry(entry.name))
                zin.getInputStream(entry).copyTo(out)
                out.closeEntry()
                total++
            }
            zin.close()
        }
        out.close()
        println("merged $total entries -> $outJar")
    }
}
