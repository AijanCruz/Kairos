package com.campusflow.app.data

import androidx.room.withTransaction

class WorkoutRepository(private val repository: CampusRepository) {
    suspend fun save(routine: WorkoutRoutine, exercises: List<Exercise>) {
        require(routine.name.isNotBlank()) { "Escribe el nombre de la rutina." }
        require(routine.minutes in 1..1440) { "Revisa la duración de la rutina." }
        require(exercises.isNotEmpty()) { "Agrega al menos un ejercicio." }
        require(exercises.all { it.name.isNotBlank() && it.sets in 1..100 && it.restSeconds in 0..3600 && it.reps.isNotBlank() }) { "Revisa los ejercicios: nombre, series, repeticiones y descanso." }
        repository.db.withTransaction {
            val inserted = repository.dao.saveRoutine(routine.copy(name = routine.name.trim()))
            val id = if (routine.id == 0L) inserted else routine.id
            repository.dao.clearExercises(id)
            repository.dao.insertExercises(exercises.mapIndexed { index, exercise -> exercise.copy(id = 0, routineId = id, position = index, name = exercise.name.trim()) })
        }
    }
    suspend fun delete(routine: WorkoutRoutine) = repository.dao.deleteRoutine(routine)
    suspend fun createExample() {
        save(WorkoutRoutine(name = "Torso A", minutes = 50), listOf("Flexiones con pies elevados", "Flexiones normales", "Remo con mochila", "Remo unilateral", "Curl martillo", "Extensión de tríceps", "Elevaciones laterales").mapIndexed { index, name -> Exercise(routineId = 0, position = index, name = name, sets = 3) })
    }
}
