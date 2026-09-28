# Primeros pasos

## Instalar en tu Galaxy A55

1. Copia `app/build/outputs/apk/debug/app-debug.apk` al teléfono y ábrelo.
2. Autoriza a la aplicación desde la que abres el APK para instalarlo, si Android
   lo solicita. Es una compilación de prueba firmada con la clave de desarrollo.
3. Abre CampusFlow. En el engranaje → Notificaciones, permite las notificaciones
   y habilita el acceso a alarmas exactas.
4. En los ajustes de batería de Samsung, comprueba que CampusFlow no esté en
   «Aplicaciones en suspensión profunda».

La aplicación empieza vacía: no mezcla tus datos con ejemplos ni estadísticas
ficticias. La rutina Torso A de ejemplo se crea únicamente cuando pulsas su botón.

## Organizar el día

- **+ → Agregar actividad:** nombre, categoría, fecha, hora, duración o fin,
  repetición, recordatorio y notas.
- **Horario:** usa las flechas para cambiar semana, los días para consultar una
  jornada, o Agenda para ver los siete días en lista.
- **Tarjeta → detalles:** completar, editar esta actividad, editar próximas
  actividades de una serie, posponer o eliminar.
- **Completar / Eliminar / Posponer:** la barra inferior ofrece DESHACER.
- **Pospuestas:** llevan la etiqueta ↪. Los detalles conservan el origen y todos
  los traslados, también los deshechos.
- **Pendientes de otros días:** aparecen en una sección desplegable de Inicio.

Editar una actividad concreta crea una excepción. Editar una serie cambia las
próximas actividades pendientes sin alterar las completadas, anteriores o
pospuestas. «Detener repetición» cancela las próximas pendientes de esa serie.

## Estudio

Agrega las materias en Estudio. Puedes asociarlas tanto a las clases como a las
sesiones. Programa una sesión y elige completarla directamente o iniciar el
temporizador. Se permite un temporizador a la vez.

El contador conserva su estado al cambiar de pantalla o cerrar la app. Pausar
detiene la cuenta; Finalizar registra el tiempo transcurrido y completa la sesión.
Al llegar a cero se muestra el aviso en la pantalla de estudio; la finalización
requiere confirmación. Los recordatorios de la actividad son independientes.

## Entrenamiento

En Entreno → + Rutina puedes agregar ejercicios con series, repeticiones,
descanso, peso y notas. «Asignar a días» abre el editor con repetición semanal;
selecciona los días deseados. En el entrenamiento de hoy se muestran los ejercicios.
Para moverlo, abre su tarjeta y elige Posponer / reprogramar.

## Importar una fotografía

1. **+ → Importar horario desde imagen → Elegir foto del horario**.
2. Espera al OCR local. Puedes consultar el texto reconocido.
3. Corrige cada materia, día y rango horario; quita falsos positivos y agrega
   materias faltantes. Las horas no reconocidas requieren confirmación.
4. Elige la fecha a partir de la cual se repite semanalmente.
5. Pulsa **Guardar horario**. Hasta ese momento no se crean actividades.

El intérprete inicial reconoce líneas y tablas sencillas por reglas y posiciones.
No es un modelo de IA: celdas combinadas, horarios partidos, varias materias en
una celda o imágenes poco nítidas pueden necesitar corrección manual. El OCR
latino viene dentro del APK y no necesita descargar un modelo.

## Apariencia y datos

Configuración permite Claro, Oscuro o Seguir sistema; Android 12+ también ofrece
colores dinámicos. Los valores predeterminados de recordatorio y estudio se aplican
a nuevas actividades.

**Exportar copia de datos** genera un JSON donde tú elijas. Incluye horarios,
materias, rutinas, ejercicios, sesiones, temporizador pausado e historial. No
incluye la apariencia ni fotos. **Restaurar copia** reemplaza los datos actuales
tras confirmar; una copia inválida no debe dejar una restauración parcial.

## Recordatorios y límites del sistema

Los avisos usan AlarmManager y se reconstruyen con WorkManager, al reiniciar,
al cambiar la hora/zona o al actualizar la app. Se mantiene una cola de hasta
80 próximas alarmas para respetar los límites del sistema; se renueva al entregar
avisos y mediante mantenimiento periódico.

Si el teléfono termina de arrancar después de la hora de una alarma programada,
se recupera el aviso mientras la actividad siga en curso, con un límite de tres
horas desde la hora original del recordatorio. No se notifican actividades pasadas
recién creadas ni avisos ya entregados.

Sin permiso de notificaciones no se muestran avisos. Sin acceso a alarmas exactas,
Android puede retrasarlos. «Forzar detención» bloquea el trabajo de una aplicación
hasta que vuelvas a abrirla. El modo de ahorro extremo o restricciones del
fabricante también pueden afectar a las notificaciones.
