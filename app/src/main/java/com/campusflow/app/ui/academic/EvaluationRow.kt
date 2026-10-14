package com.campusflow.app.ui.academic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.campusflow.app.data.*
import com.campusflow.app.domain.*
import com.campusflow.app.ui.theme.preparationColor
import com.campusflow.app.ui.theme.subjectAccent
import com.campusflow.app.ui.components.IconBadge
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Quiz

@Composable
fun EvaluationRow(item: EvaluationWithTopics, today: Long, showGrades: Boolean = false, onOpen: () -> Unit) {
    val evaluation = item.evaluation
    val prepared = AcademicRules.prepared(item)
    val urgency = AcademicRules.urgency(evaluation.date, today)
    val green = preparationColor("GREEN")
    val accent = when {
        evaluation.completed -> MaterialTheme.colorScheme.outline
        prepared -> green
        urgency == AcademicUrgency.GREEN -> green
        urgency == AcademicUrgency.YELLOW -> preparationColor("YELLOW")
        else -> MaterialTheme.colorScheme.error
    }
    val state = when {
        evaluation.completed -> "Completada"
        prepared -> "Preparado"
        urgency == AcademicUrgency.GREEN -> "Verde: más de 14 días"
        urgency == AcademicUrgency.YELLOW -> "Amarillo: de 8 a 14 días"
        else -> "Rojo: 7 días o menos"
    }
    val remaining = item.topics.count { it.status != TopicStatus.DOMINADO.name }
    val detail = when {
        evaluation.completed -> "Completada"
        prepared -> "Preparado"
        !AcademicRules.supportsTopics(evaluation.type) -> null
        item.topics.isEmpty() -> "Sin temas definidos"
        remaining == 1 -> "1 tema pendiente"
        else -> "$remaining temas pendientes"
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Ver evaluación", onClick = onOpen)
            .semantics(mergeDescendants = true) { stateDescription = state }.padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(accent, CircleShape))
        IconBadge(if (AcademicRules.supportsTopics(evaluation.type)) Icons.Outlined.Quiz else Icons.Outlined.Assignment, subjectAccent(evaluation.subjectId))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(evaluation.title, style = MaterialTheme.typography.titleMedium)
            Text("${item.subject.name} · ${EvaluationType.valueOf(evaluation.type).label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(listOfNotNull(AcademicRules.dateLabel(evaluation.date, today), detail).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = if (prepared) accent else MaterialTheme.colorScheme.onSurfaceVariant)
            if (showGrades) {
                val contribution = AcademicRules.contribution(evaluation)
                Text("${AcademicRules.numberLabel(evaluation.percentage)} % · " + if (contribution == null) "Sin calificar" else "Nota ${AcademicRules.numberLabel(evaluation.grade!!)} / 100 · Aporte ${AcademicRules.numberLabel(contribution)} puntos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
