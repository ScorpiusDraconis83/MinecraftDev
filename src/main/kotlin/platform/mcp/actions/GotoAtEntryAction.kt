/*
 * Minecraft Development for IntelliJ
 *
 * https://mcdev.io/
 *
 * Copyright (C) 2023 minecraft-dev
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

package com.demonwav.mcdev.platform.mcp.actions

import com.demonwav.mcdev.facet.MinecraftFacet
import com.demonwav.mcdev.platform.mcp.McpModuleType
import com.demonwav.mcdev.platform.mcp.mappings.MappingsManager
import com.demonwav.mcdev.platform.mixin.handlers.ShadowHandler
import com.demonwav.mcdev.util.ActionData
import com.demonwav.mcdev.util.getDataFromActionEvent
import com.demonwav.mcdev.util.gotoTargetElement
import com.demonwav.mcdev.util.qualifiedMemberReference
import com.demonwav.mcdev.util.simpleQualifiedMemberReference
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.module.ModuleManager
import com.intellij.psi.PsiField
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext

class GotoAtEntryAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val data = getDataFromActionEvent(e) ?: return showBalloon(e)

        if (data.element !is PsiIdentifier) {
            showBalloon(e)
            return
        }

        val mappingsManager = data.instance.getModuleOfType(McpModuleType)?.mappingsManager
            // Not all ATs are in MCP modules, fallback to this if possible
            // TODO try to find SRG references for all modules if current module isn't found?
            ?: MappingsManager.findAnyInstance(data.project) ?: return showBalloon(e)

        mappingsManager.mappings.onSuccess { srgMap ->
            var parent = data.element.parent

            if (parent is PsiMember) {
                val shadowTarget = ShadowHandler.getInstance()?.findFirstShadowTargetForNavigation(parent)?.element
                if (shadowTarget != null) {
                    parent = shadowTarget
                }
            }

            when (parent) {
                is PsiField -> {
                    val reference = srgMap.getIntermediaryField(parent) ?: parent.simpleQualifiedMemberReference
                    searchForText(e, data, reference.name)
                }
                is PsiMethod -> {
                    val reference = srgMap.getIntermediaryMethod(parent) ?: parent.qualifiedMemberReference
                    searchForText(e, data, reference.name + reference.descriptor)
                }
                else ->
                    showBalloon(e)
            }
        }
    }

    private fun searchForText(e: AnActionEvent, data: ActionData, text: String) {
        val manager = ModuleManager.getInstance(data.project)
        manager.modules.asSequence()
            .mapNotNull { MinecraftFacet.getInstance(it, McpModuleType) }
            .flatMap { it.accessTransformers.asSequence() }
            .forEach { virtualFile ->
                val file = PsiManager.getInstance(data.project).findFile(virtualFile) ?: return@forEach

                var found = false
                PsiSearchHelper.getInstance(data.project)
                    .processElementsWithWord(
                        { element, _ ->
                            gotoTargetElement(element, data.editor, data.file)
                            found = true
                            false
                        },
                        LocalSearchScope(file),
                        text,
                        UsageSearchContext.ANY,
                        true,
                    )

                if (found) {
                    return
                }
            }

        showBalloon(e)
    }

    private fun showBalloon(e: AnActionEvent) {
        SrgActionBase.showBalloon("No access transformer entry found", e)
    }
}
