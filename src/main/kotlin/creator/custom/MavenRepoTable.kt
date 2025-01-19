/*
 * Minecraft Development for IntelliJ
 *
 * https://mcdev.io/
 *
 * Copyright (C) 2025 minecraft-dev
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, version 3.0 only.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.demonwav.mcdev.creator.custom

import com.demonwav.mcdev.MinecraftSettings
import com.demonwav.mcdev.asset.MCDevBundle
import com.intellij.ide.BrowserUtil
import com.intellij.ide.actions.RevealFileAction
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.MutableProperty
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import java.awt.Dimension
import javax.swing.DefaultCellEditor
import javax.swing.JPanel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer
import kotlin.reflect.KMutableProperty1

private abstract class PropertyColumnInfo<I, A>(
    private val property: KMutableProperty1<I, A>,
    name: @NlsContexts.ColumnName String,
    private val tooltip: @NlsContexts.Tooltip String? = null
) : ColumnInfo<I, A>(name) {

    override fun valueOf(item: I): A = property.get(item)

    override fun setValue(item: I, value: A) = property.set(item, value)

    override fun isCellEditable(item: I): Boolean = true

    override fun getTooltipText(): @NlsContexts.Tooltip String? = tooltip
}

private object IdColumn : PropertyColumnInfo<MinecraftSettings.MavenRepo, String>(
    MinecraftSettings.MavenRepo::id,
    MCDevBundle("minecraft.settings.creator.maven.column.id"),
    MCDevBundle("minecraft.settings.creator.maven.column.id.tooltip"),
)

private object UrlColumn : PropertyColumnInfo<MinecraftSettings.MavenRepo, String>(
    MinecraftSettings.MavenRepo::url,
    MCDevBundle("minecraft.settings.creator.maven.column.url"),
    MCDevBundle("minecraft.settings.creator.maven.column.url.tooltip"),
)

private object UsernameColumn : PropertyColumnInfo<MinecraftSettings.MavenRepo, String>(
    MinecraftSettings.MavenRepo::username,
    MCDevBundle("minecraft.settings.creator.maven.column.username"),
)

private object PasswordColumn : PropertyColumnInfo<MinecraftSettings.MavenRepo, String?>(
    MinecraftSettings.MavenRepo::password,
    MCDevBundle("minecraft.settings.creator.maven.column.password"),
) {
    override fun setValue(item: MinecraftSettings.MavenRepo, value: String?) {
        super.setValue(item, value)
        getEditor(item).passwordHidden = !value.isNullOrBlank()
    }

    override fun getCustomizedRenderer(
        o: MinecraftSettings.MavenRepo?,
        renderer: TableCellRenderer?
    ): TableCellRenderer = PasswordTableCell

    override fun getEditor(item: MinecraftSettings.MavenRepo): PasswordTableCellEditor = PasswordTableCellEditor
}

@Suppress("JavaIoSerializableObjectMustHaveReadResolve")
private object PasswordTableCellEditor : DefaultCellEditor(JBPasswordField()) {

    var passwordHidden: Boolean
        get() = (component as JBPasswordField).emptyText.text.isNotBlank()
        set(value) {
            (component as JBPasswordField).setPasswordIsStored(value)
        }
}

@Suppress("JavaIoSerializableObjectMustHaveReadResolve")
private object PasswordTableCell : DefaultTableCellRenderer() {

    override fun setValue(value: Any?) {
        text = "*".repeat((value as? String)?.length ?: 0)
    }
}

fun Row.mavenRepoTable(
    prop: MutableProperty<List<MinecraftSettings.MavenRepo>>
): Cell<JPanel> {
    val model =
        object : ListTableModel<MinecraftSettings.MavenRepo>(IdColumn, UrlColumn, UsernameColumn, PasswordColumn) {
            override fun addRow() {
                val defaultName = MCDevBundle("minecraft.settings.creator.maven.default_id")
                addRow(MinecraftSettings.MavenRepo(defaultName, "", "", ""))
            }
        }
    val table = TableView<MinecraftSettings.MavenRepo>(model)
    table.setShowGrid(true)
    table.tableHeader.reorderingAllowed = false

    val decoratedTable = ToolbarDecorator.createDecorator(table)
        .setPreferredSize(Dimension(JBUI.scale(300), JBUI.scale(200)))
        .createPanel()
    return cell(decoratedTable)
        .bind(
            { _ -> model.items },
            { _, original ->
                val repos = original.toMutableList()
                // Need a copy to not affect the original when populating passwords
                for (repo in repos) {
                    if (repo.username.isNotBlank()) {
                        val credentials = CreatorCredentials.getCredentials(repo.url, repo.username)
                        repo.password = credentials?.getPasswordAsString() ?: ""
                    }
                }
                model.items = repos
            },
            prop
        ).onApply {
            for (repo in model.items) {
                if (!repo.password.isNullOrBlank()) {
                    CreatorCredentials.persistCredentials(repo.url, repo.username, repo.password!!)
                }
            }
        }.comment(MCDevBundle("minecraft.settings.creator.maven.comment")) { event ->
            when (event.description) {
                "mcdev://maven_settings" -> RevealFileAction.openFile(CreatorCredentials.getMavenSettingsPath())
                "mcdev://gradle_properties" -> RevealFileAction.openFile(CreatorCredentials.getGradleProperties())
                else -> BrowserUtil.browse(event.url)
            }
        }
}
