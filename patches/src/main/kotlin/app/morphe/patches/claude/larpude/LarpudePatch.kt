package app.morphe.patches.claude.larpude

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val EXTENSION_CLASS = "Lapp/morphe/extension/claude/Larpude;"

/*
 * All names below are obfuscated and belong to Claude 1.261106.19. Another version will most
 * likely need new names (see README, "Updating for a new Claude version").
 *
 *  com.anthropic.claude.mainactivity.MainActivity  launcher activity
 *  settings.o.b(Ly2m;)List   builds the list of Settings rows (each row is an eea0)
 *  eea0(String id, String label, hn7 icon, String subtitle, vda0 action)   one Settings row
 *  jkm.d(Object,Object)      lambda that runs when a row is tapped; looks the row up by id
 */

internal object MainActivityOnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/anthropic/claude/mainactivity/MainActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
)

internal object SettingsRowsFingerprint : Fingerprint(
    definingClass = "Lcom/anthropic/claude/settings/o;",
    name = "b",
    returnType = "Ljava/util/List;",
    parameters = listOf("Ly2m;"),
)

internal object SettingsRowClickFingerprint : Fingerprint(
    definingClass = "Ljkm;",
    name = "d",
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;", "Ljava/lang/Object;"),
)

@Suppress("unused")
val larpudePatch = bytecodePatch(
    name = "Larpude",
    description = "Adds a Larpude row above Profile in Claude's Settings that opens a Hello screen.",
) {
    compatibleWith(
        Compatibility(
            name = "Claude",
            packageName = "com.anthropic.claude",
        ),
    )

    extendWith("extensions/claude.mpe")

    execute {
        // 1. Remember the running activity (needed to show the Hello screen).
        MainActivityOnCreateFingerprint.method.addInstruction(
            0,
            "invoke-static/range { p0 .. p0 }, " +
                "$EXTENSION_CLASS->onMainActivityCreate(Landroid/app/Activity;)V",
        )

        // 2. Put the Larpude row first in the list of Settings rows.
        SettingsRowsFingerprint.method.apply {
            val returnIndex = instructions.indexOfLast { it.opcode == Opcode.RETURN_OBJECT }
            if (returnIndex < 0) throw PatchException("No return in the Settings rows method")
            val register = (instructions[returnIndex] as OneRegisterInstruction).registerA

            addInstructions(
                returnIndex,
                """
                    invoke-static { v$register }, $EXTENSION_CLASS->addRow(Ljava/util/List;)Ljava/util/List;
                    move-result-object v$register
                """,
            )
        }

        // 3. When the tapped row is Larpude, show our screen and make the app's own click
        //    code see "no row" so it does nothing else. The row lookup is
        //    `check-cast vX, Leea0;  if-eqz vX, :end`, so a null here skips everything.
        SettingsRowClickFingerprint.method.apply {
            val actionIndex = instructions.indexOfFirst {
                val ref = (it as? ReferenceInstruction)?.reference as? FieldReference
                it.opcode == Opcode.IGET_OBJECT &&
                    ref != null && ref.definingClass == "Leea0;" && ref.name == "e"
            }
            if (actionIndex < 0) throw PatchException("Settings row action read not found")

            val castIndex = (actionIndex downTo 0).firstOrNull { index ->
                val instruction = instructions[index]
                val type = ((instruction as? ReferenceInstruction)?.reference as? TypeReference)?.type
                instruction.opcode == Opcode.CHECK_CAST && type == "Leea0;"
            } ?: throw PatchException("Settings row cast not found")

            val register = (instructions[castIndex] as OneRegisterInstruction).registerA

            addInstructions(
                castIndex,
                """
                    invoke-static { v$register }, $EXTENSION_CLASS->interceptRow(Ljava/lang/Object;)Ljava/lang/Object;
                    move-result-object v$register
                """,
            )
        }
    }
}
