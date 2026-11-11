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
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val EXTENSION_CLASS = "Lapp/morphe/extension/claude/Larpude;"

private const val SCREEN_TYPE = "Lcom/anthropic/claude/settings/SettingsAppScreen;"

/*
 * Claude is obfuscated and the names change on every release, so nothing below depends on an
 * obfuscated name except the (readable) launcher activity and SettingsAppScreen. Everything else
 * is found by what it does:
 *
 *  - rows builder: a one-parameter method returning a List that calls a method returning
 *    SettingsAppScreen (it maps each Settings entry to its screen)
 *  - row class:    the 5-argument constructor (String, String, icon, String, action) that
 *    the rows builder calls for each row
 *  - tap handler:  the method outside the row class that reads the row's action field
 */

internal object MainActivityOnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/anthropic/claude/mainactivity/MainActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
)

internal object SettingsRowsFingerprint : Fingerprint(
    returnType = "Ljava/util/List;",
    custom = { method, _ ->
        method.parameterTypes.size == 1 &&
            (method.implementation?.instructions?.any {
                val ref = (it as? ReferenceInstruction)?.reference as? MethodReference
                ref != null && ref.returnType == SCREEN_TYPE
            } ?: false)
    },
)

// Filled in by the patch after the rows builder is found; read lazily by the fingerprint below.
private var rowType = ""
private var actionType = ""

internal object SettingsRowClickFingerprint : Fingerprint(
    custom = { method, classDef ->
        rowType.isNotEmpty() &&
            classDef.type != rowType &&
            (method.implementation?.instructions?.any {
                val ref = (it as? ReferenceInstruction)?.reference as? FieldReference
                it.opcode == Opcode.IGET_OBJECT &&
                    ref != null && ref.definingClass == rowType && ref.type == actionType
            } ?: false)
    },
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
            val rowConstructor = instructions
                .mapNotNull { (it as? ReferenceInstruction)?.reference as? MethodReference }
                .firstOrNull {
                    it.name == "<init>" && it.parameterTypes.size == 5 &&
                        it.parameterTypes[0].toString() == "Ljava/lang/String;" &&
                        it.parameterTypes[1].toString() == "Ljava/lang/String;" &&
                        it.parameterTypes[3].toString() == "Ljava/lang/String;"
                } ?: throw PatchException("Settings row constructor not found")
            rowType = rowConstructor.definingClass
            actionType = rowConstructor.parameterTypes[4].toString()

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
                    ref != null && ref.definingClass == rowType && ref.type == actionType
            }
            if (actionIndex < 0) throw PatchException("Settings row action read not found")

            val castIndex = (actionIndex downTo 0).firstOrNull { index ->
                val instruction = instructions[index]
                val type = ((instruction as? ReferenceInstruction)?.reference as? TypeReference)?.type
                instruction.opcode == Opcode.CHECK_CAST && type == rowType
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
