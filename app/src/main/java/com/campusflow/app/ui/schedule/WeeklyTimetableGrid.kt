package com.campusflow.app.ui.schedule

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.campusflow.app.data.*
import com.campusflow.app.domain.*
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.TextStyle
import kotlinx.coroutines.launch
import com.campusflow.app.ui.theme.categoryColors
import androidx.compose.foundation.BorderStroke

private val HourHeight = 72.dp
private val TimeWidth = 48.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeeklyTimetableGrid(schedules: List<Schedule>, onOpen: (Schedule) -> Unit, onAdd: (Int, Int) -> Unit, modifier: Modifier = Modifier) {
    val blocks = remember(schedules) { WeeklyTimetable.blocks(schedules) }
    val density = LocalDensity.current
    val axis = remember(blocks, density.fontScale) { WeeklyTimetable.readableAxis(blocks, HourHeight.value, 56f * density.fontScale.coerceAtLeast(1f)) }
    val horizontal = rememberScrollState()
    val initialScroll = with(density) { axis.offset(7 * 60).dp.roundToPx() }
    val vertical = rememberScrollState(initialScroll)
    val scope = rememberCoroutineScope()
    var jumpMenu by remember { mutableStateOf(false) }
    var focusedDay by rememberSaveable { mutableIntStateOf(1) }
    var positioned by rememberSaveable { mutableStateOf(false) }
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    BoxWithConstraints(modifier) {
        val basicWidth = maxOf(128.dp, (maxWidth - TimeWidth) / 7)
        val widths = (1..7).associateWith { day -> maxOf(basicWidth, 120.dp * (blocks.filter { it.weekday == day }.maxOfOrNull { it.lanes } ?: 1)) }
        fun xPosition(day: Int) = with(density) { (1 until day).fold(0.dp) { sum, previous -> sum + widths.getValue(previous) }.roundToPx() }
        fun yPosition(minute: Int) = with(density) { (axis.offset(minute) - 12f).coerceAtLeast(0f).dp.roundToPx() }
        fun jump(day: Int, minute: Int) {
            positioned = true
            focusedDay = day
            scope.launch {
                launch { horizontal.animateScrollTo(xPosition(day)) }
                vertical.animateScrollTo(yPosition(minute))
            }
        }
        LaunchedEffect(blocks) {
            if (!positioned && blocks.isNotEmpty()) {
                val first = blocks.firstOrNull { it.weekday == LocalDateTime.now().dayOfWeek.value } ?: blocks.first()
                positioned = true
                focusedDay = first.weekday
                horizontal.scrollTo(xPosition(first.weekday))
                vertical.scrollTo(yPosition(first.startMinute))
            }
        }
        Column(Modifier.fillMaxSize()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { val now = LocalDateTime.now(); jump(now.dayOfWeek.value, now.hour * 60 + now.minute) }) { Text("Ir a hoy") }
                Box {
                    TextButton(enabled = blocks.isNotEmpty(), onClick = { jumpMenu = true }) { Text("Ir a actividad") }
                    DropdownMenu(expanded = jumpMenu, onDismissRequest = { jumpMenu = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                        blocks.forEach { block ->
                            DropdownMenuItem(text = { Text("${DayOfWeek.of(block.weekday).getDisplayName(TextStyle.SHORT, Spanish)} · ${block.startMinute.clock()} · ${block.schedule.title}") }, onClick = {
                                jumpMenu = false; jump(block.weekday, block.startMinute)
                            })
                        }
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(Triple(Icons.Outlined.School, "Clases", categoryColors(Categories.UNIVERSITY).accent), Triple(Icons.AutoMirrored.Outlined.MenuBook, "Estudio", categoryColors(Categories.STUDY).accent), Triple(Icons.Outlined.FitnessCenter, "Entreno", categoryColors(Categories.WORKOUT).accent)).forEach { (icon, label, color) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(icon, null, Modifier.size(14.dp), tint = color)
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Hora", Modifier.width(TimeWidth), style = MaterialTheme.typography.labelSmall)
                Row(Modifier.horizontalScroll(horizontal).testTag("timetable-days")) {
                    (1..7).forEach { day ->
                        Surface(onClick = { jump(day, blocks.firstOrNull { it.weekday == day }?.startMinute ?: 8 * 60) }, color = if (focusedDay == day) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            modifier = Modifier.width(widths.getValue(day)).height(48.dp).testTag("timetable-day-$day").semantics { selected = focusedDay == day }) {
                            Box(contentAlignment = Alignment.Center) { Text(DayOfWeek.of(day).getDisplayName(TextStyle.FULL, Spanish).replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.labelLarge) }
                        }
                    }
                }
            }
            Row(Modifier.weight(1f).verticalScroll(vertical).testTag("timetable-hours").padding(bottom = 88.dp)) {
                Box(Modifier.width(TimeWidth).height(axis.height.dp)) {
                    (0..23).forEach { hour ->
                        Text((hour * 60).clock(), Modifier.offset(y = axis.offset(hour * 60).dp).padding(top = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(Modifier.horizontalScroll(horizontal).testTag("timetable-columns")) {
                    (1..7).forEach { day ->
                        val width = widths.getValue(day)
                        val dayName = DayOfWeek.of(day).getDisplayName(TextStyle.FULL, Spanish)
                        Box(Modifier.width(width).height(axis.height.dp)) {
                            Canvas(Modifier.matchParentSize()) {
                                (0..24).forEach { hour ->
                                    val y = axis.offset(hour * 60).dp.toPx()
                                    drawLine(lineColor, Offset(0f, y), Offset(size.width, y))
                                }
                                drawLine(lineColor, Offset.Zero, Offset(0f, size.height))
                            }
                            (0..23).forEach { hour ->
                                Box(Modifier.offset(y = axis.offset(hour * 60).dp).width(width).height((axis.offset((hour + 1) * 60) - axis.offset(hour * 60)).dp)
                                    .testTag("timetable-slot-$day-${hour * 60}")
                                    .semantics { contentDescription = "Agregar bloque el $dayName a las ${(hour * 60).clock()}" }
                                    .clickable(onClickLabel = "Agregar bloque semanal") { onAdd(day, hour * 60) })
                            }
                            blocks.filter { it.weekday == day }.forEach { block ->
                                val palette = categoryColors(block.schedule.category)
                                val time = "${block.startMinute.clock()}–${block.endMinute.clock()}"
                                val description = "${block.schedule.title}, ${Categories.label(block.schedule.category)}, $dayName, $time${if (block.continuation) ", continúa del día anterior" else ""}"
                                val blockHeight = (axis.offset(block.endMinute) - axis.offset(block.startMinute) - 2f).dp
                                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                                    Surface(
                                        onClick = { onOpen(block.schedule) }, color = palette.container, contentColor = palette.content,
                                        border = BorderStroke(1.dp, palette.accent.copy(alpha = .5f)),
                                        shape = MaterialTheme.shapes.extraSmall,
                                        modifier = Modifier.offset(x = width / block.lanes * block.lane + 2.dp, y = axis.offset(block.startMinute).dp)
                                            .width(width / block.lanes - 4.dp)
                                            .height(blockHeight)
                                            .testTag("timetable-block-${block.schedule.id}-$day-${block.continuation}")
                                            .semantics { contentDescription = description },
                                    ) {
                                        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(when (block.schedule.category) {
                                                    Categories.UNIVERSITY -> Icons.Outlined.School
                                                    Categories.STUDY -> Icons.AutoMirrored.Outlined.MenuBook
                                                    Categories.WORKOUT -> Icons.Outlined.FitnessCenter
                                                    else -> Icons.Outlined.Event
                                                }, null, Modifier.size(14.dp), tint = palette.accent)
                                                Text(block.schedule.title, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, maxLines = if (blockHeight >= 80.dp) 2 else 1, overflow = TextOverflow.Ellipsis)
                                            }
                                            Text(time, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            if (block.continuation && blockHeight >= 80.dp) Text("Continuación", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
