# Arquitectura

## Capas

```
ui/              Compose: pantallas, componentes, tema y navegación
data/            Room, preferencias y repositorio transaccional
domain/          Recurrencia y reglas de fechas independientes de Android
reminders/       AlarmManager, receptores y mantenimiento con WorkManager
ocr/             ML Kit on-device + interfaz de interpretación
```

El contenedor de dependencias vive en `CampusApp`. Para este tamaño se usa
inyección por constructor sin un framework adicional. Los ViewModels exponen
estado observable y eventos de feedback; las operaciones atómicas pertenecen
al repositorio y utilizan transacciones de Room.

## Modelo temporal

- Una serie almacena la regla (una vez, diaria o días semanales), hora local,
  fecha inicial, duración y enlaces a materia/rutina.
- Una ocurrencia identifica de forma única una fecha original de esa serie.
  Almacena su fecha/hora efectiva, estado y finalización.
- Las fechas de calendario se almacenan como `epochDay` y los minutos locales
  como enteros. Se resuelve la zona horaria actual al programar alarmas. Así una
  clase a las 08:00 conserva las 08:00 locales tras un cambio de zona.
- El historial de traslados conserva anterior/nueva fecha y hora. Deshacer se
  registra como reversión; no elimina la información histórica.
- Las ocurrencias futuras se materializan en un horizonte móvil. El trabajo
  periódico y la apertura de la app extienden ese horizonte.
- Navegar a otra semana también materializa sus fechas. Los cálculos resuelven
  los cambios de horario de verano con `java.time` y la zona local actual.
- La lectura de reglas activas y la generación de ocurrencias comparten una
  transacción, también al navegar semanas. Así una edición o restauración no
  puede intercalarse entre la lectura de una regla y la creación de sus fechas.
- Las cancelaciones conservan una marca para impedir que el generador vuelva
  a crear lo eliminado.
- Editar una serie crea una nueva versión para las próximas ocurrencias pendientes;
  se mantienen las antiguas para el pasado y las excepciones. Editar una sola
  ocurrencia la enlaza a una configuración independiente, conserva su ID e historial
  y deja una cancelación en la serie para que no se duplique al regenerar.
- Guardar una versión ya inactiva se rechaza como edición obsoleta. Deshacer
  compara también los datos de la ocurrencia, no solo su estado, para no
  sobrescribir una edición posterior. Las reglas se validan al guardar y restaurar.

## Recordatorios

Los recordatorios pertenecen a las series; se programan por ocurrencia con una
identidad estable. Completar, cancelar, editar o mover cancela/reconcilia alarmas.
Se programa un conjunto limitado de las próximas alarmas para respetar los
límites de Samsung; el mantenimiento renueva la cola. El receptor confirma el
estado de Room antes de mostrar una notificación y descarta alarmas obsoletas.

Programación, entrega y sustitución de datos comparten un mutex. El registro de
alarmas entregadas evita duplicar avisos al reconciliar. Las solicitudes de
reprogramación se agrupan en un canal conflado para que una importación no lance
un trabajo por cada clase. Las modificaciones de datos siempre son transaccionales.

La sustitución cancela las alarmas anteriores únicamente después de que la
transacción de restauración termine correctamente; una copia inválida deja
los avisos existentes activos. La entrega consulta el estado de la ocurrencia
por su clave primaria en vez de cargar todo el registro de recordatorios.

Tras un reinicio se intenta reconciliar directamente desde el receptor asíncrono
y se conserva WorkManager como recuperación persistente. Las alarmas ya registradas
que vencieron durante el arranque pueden entregarse mientras la actividad siga
en curso (límite de tres horas desde el aviso); las actividades pasadas recién
creadas no generan avisos retrospectivos.

El permiso de notificaciones (Android 13+) y el acceso especial a alarmas
exactas (Android 12+) se gestionan de forma explícita desde la UI.

## Temporizador y progreso

Room guarda duración total, tiempo restante e instante de inicio. Se reconstruye
el contador después de recrear la actividad/proceso. Al pausar se descuenta el
tiempo transcurrido; al finalizar se crea un registro único por ocurrencia. La
transacción evita una doble finalización. La finalización manual registra la
duración planificada; con temporizador se registra el tiempo efectivamente usado.
Reprogramar desde el editor pausa el contador igual que Posponer. Cambiar la
categoría de la sesión a otra distinta de Estudio descarta su temporizador.

El progreso de Inicio usa la fecha real de finalización para contar lo realizado
esta semana, también cuando se completan pendientes de semanas anteriores.

## Capa académica

Room v3 añade solo `academic_evaluations` (id, subjectId, title, type, date,
completed) y `exam_topics` (id, evaluationId, name, status). Las fechas son días
de calendario (`epochDay`), no instantes ni horarios. Las tablas anteriores no
cambian. Migración 2 → 3 aditiva, conservando la ruta 1 → 2 → 3.

Un DAO y repositorio pequeños exponen evaluaciones con materia y temas como Flow.
`AppViewModel` comparte ese estado y serializa las acciones académicas. Solo los
exámenes aceptan temas; las relaciones eliminan dependientes con CASCADE. Cambiar
el tipo de un examen con temas requiere eliminarlos explícitamente primero.

`AcademicRules` calcula la cercanía y preparación sin persistir colores. Inicio
muestra hasta tres evaluaciones próximas. Estudio elige un tema del examen más
cercano de la materia: Pendiente antes de Practicando; nunca Dominado. La sugerencia
es informativa. Las sesiones siguen siendo Schedule y usan el timer existente.
La nueva ruta de gestión mantiene las cuatro pestañas de navegación.

## OCR

La galería entrega una URI mediante Photo Picker. La imagen se decodifica con
muestreo para limitar memoria y se reconoce localmente. ML Kit está empaquetado
para no requerir descarga del modelo en el primer uso.

El reconocedor entrega bloques con coordenadas a una interfaz de intérprete.
La implementación inicial usa heurísticas para días, rangos horarios y líneas;
la UI siempre permite corregir, quitar o agregar entradas antes de confirmar.
No se considera el texto reconocido como instrucciones ejecutables.
La interpretación se ejecuta en `Dispatchers.Default`; la actualización del
estado permanece en el ViewModel. Los títulos se limpian sobre texto Unicode
compuesto, evitando usar índices de una cadena normalizada de distinta longitud.

## Copias y estado de operaciones

Las operaciones exportar/restaurar/borrar y su estado ocupado pertenecen a
`AppViewModel`; un guard impide iniciar otra operación mientras hay una activa,
incluso tras recrear la pantalla. El formato JSON v1 de las tablas originales se conserva. La restauración
valida fechas de reglas, ocurrencias e historial, además de los límites del timer
y su asociación a una sesión de estudio pendiente. Una validación fallida revierte
la transacción completa.
Por el alcance solicitado, el backup no incluye las dos tablas académicas de
Room v3. Restaurar reemplaza materias y elimina sus dependientes académicos por
CASCADE; esta limitación está indicada en la guía de uso y en el registro de entrega.

## Privacidad

Sin cuenta ni backend. Sin permiso `INTERNET`. Copia automática de Android
desactivada para evitar subir la base de datos a la cuenta Google. Exportación
manual de datos a un documento elegido por el usuario.
