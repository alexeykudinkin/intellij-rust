/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.icons

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ColorUtil
import com.intellij.ui.LayeredIcon
import com.intellij.util.IconUtil
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.image.RGBImageFilter
import javax.swing.Icon

/**
 * Icons that are used by various plugin components.
 *
 * The order of properties matters in this class. When conflating an icon from simple elements,
 * make sure that all those elements are declared above to the icon.
 */
object RsIcons {
    // Logos

    val RUST = load("/icons/rust.svg")

    // File types

    val RUST_FILE = load("/icons/rustFile.svg")
    val MAIN_RS = load("/icons/rustMain.svg")
    val MOD_RS = load("/icons/rustMod.svg")

    // Marks

    val FINAL_MARK = AllIcons.Nodes.FinalMark
    val STATIC_MARK = AllIcons.Nodes.StaticMark
    val TEST_MARK = AllIcons.Nodes.JunitTestMark
    val DOCS_MARK = load("/icons/rustDocs.svg")
    val FEATURE_CHECKED_MARK = AllIcons.Diff.GutterCheckBoxSelected
    val FEATURE_UNCHECKED_MARK = AllIcons.Diff.GutterCheckBox
    val FEATURE_CHECKED_MARK_GRAYED = FEATURE_CHECKED_MARK.grayed()
    val FEATURE_UNCHECKED_MARK_GRAYED = FEATURE_UNCHECKED_MARK.grayed()
    val FEATURES_SETTINGS = AllIcons.General.Settings

    // Source code elements

    val CRATE = AllIcons.Nodes.PpLib
    val MODULE = load("/icons/nodes/module.svg")

    val TRAIT = load("/icons/nodes/trait.svg")
    val STRUCT = load("/icons/nodes/struct.svg")
    val UNION = load("/icons/nodes/union.svg")
    val ENUM = load("/icons/nodes/enum.svg")
    val TYPE_ALIAS = load("/icons/nodes/typeAlias.svg")
    val IMPL = load("/icons/nodes/impl.svg")
    val FUNCTION = load("/icons/nodes/function.svg")
    val MACRO = load("/icons/nodes/macro.svg")
    val MACRO2 = load("/icons/nodes/macro2.svg")
    val PROC_MACRO = load("/icons/nodes/macroP.svg")

    val CONSTANT = load("/icons/nodes/constant.svg")
    val MUT_STATIC = load("/icons/nodes/static.svg")
    val STATIC = MUT_STATIC.addFinalMark()

    val METHOD = load("/icons/nodes/method.svg")
    val ASSOC_FUNCTION = FUNCTION.addStaticMark()
    val ASSOC_CONSTANT = CONSTANT.addStaticMark()
    val ASSOC_TYPE_ALIAS = TYPE_ALIAS.addStaticMark()

    val ABSTRACT_METHOD = load("/icons/nodes/abstractMethod.svg")
    val ABSTRACT_ASSOC_FUNCTION = load("/icons/nodes/abstractFunction.svg").addStaticMark()
    val ABSTRACT_ASSOC_CONSTANT = load("/icons/nodes/abstractConstant.svg").addStaticMark()
    val ABSTRACT_ASSOC_TYPE_ALIAS = load("/icons/nodes/abstractTypeAlias.svg").addStaticMark()

    val ATTRIBUTE = AllIcons.Nodes.Annotationtype
    val MUT_ARGUMENT = AllIcons.Nodes.Parameter
    val ARGUMENT = MUT_ARGUMENT.addFinalMark()
    val MUT_BINDING = AllIcons.Nodes.Variable
    val BINDING = MUT_BINDING.addFinalMark()

    val FIELD = load("/icons/nodes/field.svg")
    val ENUM_VARIANT = load("/icons/nodes/enumVariant.svg")

    // Structure view

    val MACRO_EXPANSION = AllIcons.Nodes.ErrorIntroduction
    val VISIBILITY_SORT = AllIcons.ObjectBrowser.VisibilitySort

    // Gutter

    val IMPLEMENTED = AllIcons.Gutter.ImplementedMethod
    val IMPLEMENTING_METHOD = AllIcons.Gutter.ImplementingMethod
    val OVERRIDING_METHOD = AllIcons.Gutter.OverridingMethod
    val RECURSIVE_CALL = AllIcons.Gutter.RecursiveMethod

    // Repl

    val REPL = load("/icons/rustRepl.svg")

    val CARGO_GENERATE = load("/icons/cargoGenerate.svg")
    val WASM_PACK = load("/icons/wasmPack.svg")

    // Progress

    val GEAR = load("/icons/gear.svg")
    val GEAR_OFF = load("/icons/gearOff.svg")
    val GEAR_ANIMATED = AnimatedIcon(AnimatedIcon.Default.DELAY, GEAR, GEAR.rotated(15.0), GEAR.rotated(30.0), GEAR.rotated(45.0))

    private fun load(path: String): Icon = IconLoader.getIcon(path, RsIcons::class.java)
}

fun Icon.addFinalMark(): Icon = LayeredIcon(this, RsIcons.FINAL_MARK)

fun Icon.addStaticMark(): Icon = LayeredIcon(this, RsIcons.STATIC_MARK)

fun Icon.addTestMark(): Icon = LayeredIcon(this, RsIcons.TEST_MARK)

fun Icon.multiple(): Icon {
    val compoundIcon = LayeredIcon(2)
    compoundIcon.setIcon(this, 0, 2 * iconWidth / 5, 0)
    compoundIcon.setIcon(this, 1, 0, 0)
    return compoundIcon
}

fun Icon.grayed(): Icon =
    IconUtil.filterIcon(this, {
        object : RGBImageFilter() {
            override fun filterRGB(x: Int, y: Int, rgb: Int): Int {
                val color = Color(rgb, true)
                return ColorUtil.toAlpha(color, (color.alpha / 2.2).toInt()).rgb
            }
        }
    }, null)

/**
 * Rotates the icon by the given angle, in degrees.
 *
 * **Important**: Do ***not*** rotate the icon by ±90 degrees (or any sufficiently close amount)!
 * The implementation of rotation by that amount in AWT is broken, and results in erratic shifts for composed
 * transformations. In other words, the (final) transformation matrix as a function of rotation angle
 * is discontinuous at those points.
 */
fun Icon.rotated(angle: Double): Icon {
    val q = this
    return object : Icon by this {
        override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
            val g2d = g.create() as Graphics2D
            try {
                g2d.translate(x.toDouble(), y.toDouble())
                g2d.rotate(Math.toRadians(angle), iconWidth / 2.0, iconHeight / 2.0)
                q.paintIcon(c, g2d, 0, 0)
            } finally {
                g2d.dispose()
            }
        }
    }
}
