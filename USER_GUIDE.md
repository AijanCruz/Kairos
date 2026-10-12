# Primeros pasos

## Instalar en tu Galaxy A55

1. Copia `app/build/outputs/apk/debug/app-debug.apk` al teléfono y ábrelo.
2. Autoriza a la aplicación desde la que abres el APK para instalarlo, si Android
   lo solicita. Es una compilación de prueba firmada con la clave de desarrollo.
3. Abre Kairos. En el engranaje → Notificaciones, permite las notificaciones
   y habilita el acceso a alarmas exactas.
4. En los ajustes de batería de Samsung, comprueba que Kairos no esté en
   «Aplicaciones en suspensión profunda».

La aplicación empieza vacía: no mezcla tus datos con ejemplos ni estadísticas
ficticias. La rutina Torso A de ejemplo se crea únicamente cuando pulsas su botón.

APK actual: `dist/Kairos-1.0.0-debug.apk`. Kairos conserva la instalación y los
datos de CampusFlow al actualizar con la misma firma; no hace falta desinstalar.

## Organizar el día

- **+ → Agregar actividad:** nombre, categoría, fecha, hora, duración o fin,
  repetición, recordatorio y notas.
- **Horario → Semana:** cuadrícula permanente del horario base, de lunes a domingo
  y de 00:00 a 24:00. Desliza horizontalmente para ver los demás días y verticalmente
  para cambiar de hora. Toca una hora libre o **+ Bloque semanal** para agregar;
  toca un bloque para editarlo o eliminarlo de todos sus días de repetición.
  **Ir a hoy** centra el día y la hora actuales; **Ir a actividad** permite saltar
  a un bloque por su nombre. Tocar el encabezado de un día lleva a su primer bloque.
  Las franjas breves se amplían para poder leerlas y tocarlas, conservando sus horas
  reales. Los bloques solapados tienen columnas más anchas e iconos por categoría.
- **Horario → Agenda:** actividades con fecha, separadas en puntuales/excepciones
  y recurrentes del día. Usa las flechas para cambiar semana y **Día / 7 días**
  para elegir el alcance. Las citas y evaluaciones no se convierten en horarios semanales.
- **Tarjeta → detalles:** completar, editar esta actividad, editar próximas
  actividades de una serie, posponer o eliminar.
- **Completar / Eliminar / Posponer:** la barra inferior ofrece DESHACER.
- **Pospuestas:** llevan la etiqueta ↪. Los detalles conservan el origen y todos
  los traslados, también los deshechos.
- **Pendientes de otros días:** aparecen en una sección desplegable de Inicio.

Editar una actividad concreta crea una excepción. Editar una serie cambia las
próximas actividades pendientes sin alterar las completadas, anteriores o
pospuestas. «Detener repetición» cancela las próximas pendientes de esa serie.
Si una serie cambió mientras estaba abierto su editor, vuelve a abrirla antes de
guardar. Deshacer se rechaza si una edición posterior cambió la actividad, para
proteger esos cambios.

El horario base muestra las reglas recurrentes activas, no sus ocurrencias:
completar, cancelar o posponer una actividad individual no borra el bloque base.
La agenda y los recordatorios respetan las fechas de inicio/fin de cada regla;
la plantilla permanece visible hasta detenerla o retirarla. Los bloques que cruzan
medianoche se muestran en ambos días y los solapados en columnas paralelas.
Las evaluaciones académicas mantienen su gestión por materia en Estudio.

## Estudio

Agrega las materias en Estudio. Puedes asociarlas tanto a las clases como a las
sesiones. Programa una sesión y elige completarla directamente o iniciar el
temporizador. Se permite un temporizador a la vez.

El contador conserva su estado al cambiar de pantalla o cerrar la app. Pausar
detiene la cuenta; Finalizar registra el tiempo transcurrido y completa la sesión.
Al llegar a cero se muestra el aviso en la pantalla de estudio; la finalización
requiere confirmación. Los recordatorios de la actividad son independientes.
Al cambiar la fecha u hora de una sesión desde el editor, su contador se pausa.
Si cambias su categoría a otra distinta de Estudio, el temporizador se descarta.

### Evaluaciones y temas

En **Estudio → Evaluaciones de [materia] → + Evaluación** puedes crear un examen,
tarea, proyecto, quiz o exposición con título y fecha límite. Toca la evaluación
para editarla, completarla, volverla a marcar pendiente o eliminarla.

En exámenes y quizzes aparece **+ Tema**. Cada tema tiene un nombre editable y uno de
estos estados: **Pendiente**, **Practicando** o **Dominado**. Para cambiar un examen
con temas a tarea, proyecto o exposición, primero elimina sus temas. Cambiar entre
Examen y Quiz conserva todos los temas y sus estados.

El punto de color indica cercanía: verde a más de 14 días, amarillo de 8 a 14 días
y rojo a 7 días o menos (incluidas fechas vencidas). Un examen o quiz con al menos un tema
y todos dominados muestra **Preparado**, aunque esté cerca. Preparado no significa
completado: completar la evaluación es una acción separada.

Inicio muestra como máximo tres **Próximas evaluaciones**, ordenadas por fecha,
no completadas y desde hoy en adelante. Las vencidas y completadas se consultan
en la materia. Tocar una de Inicio abre su gestión con los temas desplegados.

### Porcentajes y calificaciones

Al crear o editar una evaluación, introduce su **Porcentaje de la materia (%)**
y, cuando la tengas, la **Nota obtenida (0–100)**. Se admiten decimales con coma
o punto. Deja la nota vacía si aún no está calificada; puedes borrarla después.
La suma de porcentajes de una materia no puede superar el 100 %, incluidas las
evaluaciones pendientes o completadas. El 0 % permite dejar un peso sin asignar.

La pantalla de evaluaciones de cada materia muestra:
- **Aporte individual:** nota × porcentaje / 100.
- **Puntos acumulados:** suma de los aportes, sobre 100 puntos de la nota final.
- **Promedio ponderado:** puntos acumulados × 100 / porcentaje con nota.
- **Porcentaje pendiente:** 100 menos el porcentaje con nota; incluye el aún
  no asignado. Las evaluaciones sin nota no se consideran ceros.

Por ejemplo, una nota de 80 con un peso del 20 % aporta 16 puntos; si es la única
calificada, el promedio ponderado es 80 y queda pendiente el 80 %. Los resultados
se actualizan al guardar notas o porcentajes. Sin calificaciones con peso no se
muestra un promedio numérico. Completar una evaluación y calificarla son acciones
independientes. Tras actualizar, las evaluaciones antiguas conservan sus datos
con porcentaje inicial 0 % y nota vacía.

### Meta final y resumen de temas

En las evaluaciones de una materia, pulsa **Establecer meta final** para guardar
una nota objetivo de 0 a 100. Puedes editarla o dejar el campo vacío para quitarla.
La app muestra **Meta asegurada**, **Meta alcanzable** o **Meta matemáticamente
imposible**, según los puntos acumulados y el porcentaje que quede por calificar.
Cuando es alcanzable indica el promedio ponderado necesario en ese porcentaje:
`(meta − puntos acumulados) × 100 / porcentaje pendiente`.

El cálculo no convierte notas pendientes en ceros. Si todavía no asignaste todos
los porcentajes, se indica cuánto falta y que el cálculo supone completar el 100 %
de la materia. El promedio necesario se redondea hacia arriba a dos decimales.
Debajo aparece el número de temas **Pendientes**, **Practicando** y **Dominados**
de los exámenes y quizzes de esa materia, incluidas las evaluaciones completadas.

### Sugerencia y duración de estudio

Selecciona **Materia para estudiar**. Del examen o quiz más cercano no completado y con
fecha desde hoy, se sugiere primero un tema Pendiente y luego uno Practicando.
Los Dominados se ignoran. Si esa evaluación no tiene temas por preparar, no hay
sugerencia. Cambiar la materia o ver una sugerencia no crea una sesión.

En **Duración de la nueva sesión** está disponible **40 minutos**. Pulsa
**Planificar estudio**, revisa el nombre y horario y guarda cuando lo decidas;
se preseleccionan la materia y la duración elegidas. El temporizador existente
usa la duración de esa sesión. También puedes elegir 40 minutos como valor
predeterminado desde Configuración.

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
La validación rechaza fechas, reglas o temporizadores inválidos conservando los
datos y las alarmas actuales. Las copias válidas anteriores de CampusFlow siguen
siendo compatibles. Durante exportar, restaurar o borrar, los botones de datos
permanecen bloqueados aunque gires la pantalla o vuelvas a Configuración.

Las copias nuevas (formato 2) incluyen **evaluaciones, temas, porcentajes, notas y
metas**, conservando sus relaciones con las materias. Restaurar una copia completa
reemplaza los datos por los del archivo después de confirmar.

Las copias antiguas (formato 1) siguen admitidas. Como no contienen datos académicos,
se conservan los que ya tienes, incluidas las materias necesarias para sus relaciones.
Si un ID o nombre de materia del archivo entra en conflicto con esos datos, se
rechaza la restauración entera sin modificar nada. Una copia inválida, incompleta
o con relaciones rotas tampoco se restaura parcialmente. Eliminar una materia
manualmente elimina sus evaluaciones, temas y meta.

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
