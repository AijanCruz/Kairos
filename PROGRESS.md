# Kairos — registro de desarrollo

## Capa académica sencilla — entrega completada

Implementada según el nuevo alcance limitado del usuario; no se rediseñó Inicio
ni se continuó el plan anterior de Dashboard 2.0. Revisión restringida a Room,
materias, Inicio, Estudio, navegación, tema/componentes y la interacción directa
de las nuevas relaciones con el backup existente.

### Agregado

- Evaluaciones por materia: EXAMEN, TAREA, PROYECTO, QUIZ y EXPOSICION; título,
  fecha límite y completado. Crear, editar, eliminar, completar y reabrir.
- Temas solo para EXAMEN, con nombre y estado PENDIENTE/PRACTICANDO/DOMINADO.
- Semáforo calculado: >14 días verde, 8–14 amarillo, <=7 rojo. Preparado cuando
  todos los temas están dominados y hay al menos uno; no completa automáticamente.
- Sección compacta en Inicio con hasta tres evaluaciones no completadas desde hoy,
  fecha más cercana primero. Vencidas/completadas permanecen accesibles en la materia.
- Sugerencia al seleccionar materia: examen más cercano no completado desde hoy,
  tema Pendiente antes de Practicando. Si ese examen está preparado o no tiene
  temas, no hay sugerencia. No crea sesiones automáticamente.
- Preset de 40 minutos en Estudio y preferencias. La materia y duración pasan
  al editor de sesión existente; el usuario confirma antes de guardar.
- Corregida la restauración de navegación al pulsar Inicio desde una evaluación:
  vuelve al resumen en vez de restaurar otra vez la pantalla de evaluación.

### Archivos

Nuevos bajo `app/src/main/java/com/campusflow/app/`:
`data/AcademicEntities.kt`, `data/AcademicDao.kt`, `data/AcademicRepository.kt`,
`domain/AcademicRules.kt`, `ui/academic/AcademicScreen.kt`,
`ui/academic/EvaluationEditor.kt`, `ui/academic/EvaluationRow.kt`.

Modificados: `data/CampusDatabase.kt`, `ui/AppViewModel.kt`, `ui/CampusRoot.kt`,
`ui/home/DashboardScreen.kt`, `ui/study/StudyScreen.kt`,
`ui/settings/SettingsScreen.kt`. Esquema nuevo:
`app/schemas/com.campusflow.app.data.CampusDatabase/3.json`.
Tests: `AcademicRulesTest.kt`, `AcademicRepositoryTest.kt`, `AcademicUiTest.kt`
y `MigrationTest.kt`. Documentación: README, arquitectura, guía y este registro.

### Migración y verificación

- Room 2 → 3: crea `academic_evaluations`, `exam_topics` y sus índices/claves
  foráneas; no altera las entidades/tablas anteriores. Comparación de esquemas
  confirma únicamente esas adiciones. Se probó también la ruta 1 → 2 → 3.
- `testDebugUnitTest --tests com.campusflow.app.domain.AcademicRulesTest`:
  **8 tests aprobados**, incluyendo límites 7/8/14/15 días, cambio de año,
  preparación con/sin temas, prioridades y exclusión de completados/vencidos.
- Android API 35: **21 tests de datos/migración aprobados** (5 académicos, 3 de
  migración y 13 regresiones del repositorio existente sobre Room v3).
- **2 tests Compose académicos aprobados** tras corregir un selector ambiguo del
  test y el retorno a Inicio. Cubren borrador tras recreación, creación de examen
  y tema, cambios de estado, Preparado, completar desde Inicio, sugerencia sin
  escritura automática y sesión de 40 minutos con el temporizador existente.
- `assembleDebug` y `lintDebug` correctos. Lint: **0 errores**, 16 avisos de
  versiones de dependencias ya existentes. No se añadieron dependencias.

Los tests Android se ejecutaron seleccionando `AcademicRepositoryTest`,
`MigrationTest`, `RepositoryTest`, `AcademicUiTest`; la reejecución final de UI
seleccionó `AcademicUiTest`. No se ejecutó una auditoría completa de la app.

APK: `dist/Kairos-1.0.0-debug.apk`.
SHA-256: `3ab069f8b249051522ceade36b4f42472fe2ee64fe13a5eae9a78bfca83f32cf`.

### Limitaciones pendientes

El usuario excluyó cambios de Backup: su código/formato no se modificó y las
nuevas evaluaciones/temas **no se exportan**. Restaurar un JSON reemplaza materias
y elimina sus evaluaciones/temas por CASCADE, sin poder recuperarlos desde ese
archivo. La migración de actualización, en cambio, conserva todos los datos
preexistentes. Se documentó la limitación en la guía de uso.

Pruebas realizadas en emulador, no en teléfono físico. El semáforo tiene etiquetas
de accesibilidad y el preparado se expresa también con texto, no solo color.

---

## Fase 1 — estabilización completada

Entrega limitada a estabilización. El nombre Kairos y el PNG con transparencia
provienen del cambio de identidad ya solicitado. Las fases 2 y 3 siguen pendientes.

### Cambios implementados

| Archivos (bajo `app/src/main/java/com/campusflow/app/`) | Corrección |
| --- | --- |
| `data/CampusRepository.kt` | Generación de fechas transaccional; evita intercalado con edición de series/restauración. Pausa compartida del timer al mover desde editor o Posponer; descarta timer si deja de ser estudio. Rechaza edición de ocurrencias canceladas, recordatorios inválidos y series obsoletas. Deshacer comprueba también los datos para no pisar una edición. |
| `domain/CalendarRules.kt` | Validación común de fechas, fecha final, repetición y todos los días semanales. También valida el inicio efectivo al versionar una serie. |
| `data/CampusDao.kt`, `reminders/ReminderScheduler.kt` | Consulta individual de estado de alarma. Una restauración fallida no cancela las alarmas existentes. |
| `data/DataBackup.kt` | Valida fechas de reglas/origen/historial, horas del historial, título y límites del timer. Verifica que el timer pertenezca a una sesión de estudio pendiente, con rollback completo si falla. |
| `ui/AppViewModel.kt`, `ui/settings/SettingsScreen.kt` | Operaciones de datos y estado ocupado en ViewModel, con exclusión de solicitudes simultáneas; sobreviven a recreación de pantalla sin capturar su estado local. |
| `ocr/ImportViewModel.kt` | Interpretación de texto fuera del hilo principal. |
| `ocr/ScheduleInterpreter.kt` | Limpieza de títulos con acentos descompuestos sin desplazar índices; rechaza minutos malformados como `09:300`. |
| `ui/settings/SettingsScreen.kt`, `NotificationControls.kt`, `ui/schedule/ScheduleScreen.kt` | Etiquetas para interruptores; fecha completa, presencia de actividades y selección expuestas a accesibilidad del horario. |

Otros recursos: `AndroidManifest.xml` explicita `fullBackupContent=false`;
`res/drawable-nodpi/kairos_logo.png` conserva el mismo PNG sin reescalado por
densidad; eliminado `res/drawable/ic_launcher.xml`, ya sin referencias.

**Base de datos:** esquema Room v2 sin cambios; no requiere migración nueva.
La prueba de migración 1 → 2 sigue pasando. JSON v1 y marcador `CampusFlow`
conservados; no se añadieron dependencias ni datos de ejemplo.

### Revisión adicional

- Se revisaron entidades, DAO, repositorios, ViewModels, navegación, pantallas,
  temporizador, recordatorios, OCR, tests y documentación antes de modificar.
- Series, excepciones e historial: pruebas de edición/movimiento/completado,
  concurrencia y rechazo de editores obsoletos.
- Fechas: cobertura JVM de cambio de zona, hueco y solapamiento de horario de
  verano, cambio de año y fin de recurrencia. Se conserva la política de `java.time`.
- Compose y cálculos: no se dividieron pantallas por tamaño ni se rediseñó Inicio;
  se priorizó el estado de operaciones largas y la semántica de controles.
- OCR/memoria: se revisaron muestreo de imágenes, cierre del reconocedor y scopes.
  No se hizo perfilado de heap ni se afirma ausencia de todas las fugas. La revisión
  manual antes de guardar sigue siendo obligatoria.
- Los 16 avisos restantes de Lint son `GradleDependency`. Se mantienen las versiones
  verificadas; no se actualizaron bibliotecas en bloque por avisos de disponibilidad.

### Verificación de esta entrega

Comando final:
`./build.ps1 -Tasks @('assembleDebug','testDebugUnitTest','lintDebug','connectedDebugAndroidTest')`
con `ANDROID_SERIAL=emulator-5554`.

| Comprobación | Resultado |
| --- | --- |
| Build debug | Correcto |
| Tests JVM | 19, 0 fallos |
| Tests Android | 19, 0 fallos, 0 omitidos; emulador Android 15 / API 35 |
| Lint | 0 errores; 16 avisos de versiones de dependencias |
| Interfaz | Crear/recrear/completar/deshacer/posponer, rutina semanal y tema; semántica de interruptor y día seleccionado verificada |
| Alarmas | Entrega Android real, intento de restore inválido, deduplicación, traslado y rechazo de trigger obsoleto |
| Copias | Round-trip de historial; rollback ante relaciones, fechas y timers inválidos |

Pruebas añadidas: 3 de calendario/zona, 2 de OCR y 5 de repositorio. Ampliadas
la prueba de alarma y la de tema/navegación con regresiones de restore y accesibilidad.
Archivos: `CalendarRulesTest.kt`, `InterpreterTest.kt`, `RepositoryTest.kt`,
`ReminderIntegrationTest.kt`, `AppUiTest.kt`.

Evidencia reproducible de esta ejecución:
- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/reports/androidTests/connected/debug/index.html`
- `app/build/reports/lint-results-debug.html`
- `app/build/test-results/testDebugUnitTest/`
- `app/build/outputs/androidTest-results/connected/debug/`

APK: `dist/Kairos-1.0.0-debug.apk` (idéntico al APK de `app/build/outputs/apk/debug/`).
SHA-256: `00a0cd796d54b17ad021b1ab03888fdb42829d587362ccabc26eca911aaad09a`.

Límites: en esta fase no se ejecutaron los scripts de reinicio/proceso terminado,
ni pruebas en teléfono físico, ni una sesión manual de TalkBack. Los resultados de
esos escenarios del desarrollo original, debajo, son históricos, no de esta entrega.

---

## Historial de la versión original CampusFlow

## Entorno
- Windows; JDK 21 (Temurin) disponible.
- Sin SDK, Gradle ni Android Studio detectados inicialmente.
- Herramientas aisladas en `.toolchain/`; sin cambios globales.
- Kotlin / Compose / Material 3; Android 8+ (API 26), target API 35.

## Etapas
- [x] Base y primera compilación — `assembleDebug` correcto; Gradle 8.11.1, SDK 35.
- [x] 1. Arquitectura, navegación, temas — `assembleDebug` correcto.
- [x] 2. Room, actividades, horario — `assembleDebug` y 4 tests JVM correctos.
- [x] 3. Dashboard — `assembleDebug` correcto.
- [x] 4. Estudio — compilación, 7 tests JVM y 3 tests Room en emulador Android 15 correctos.
- [x] 5. Entrenamiento — `assembleDebug` correcto; rutinas y ejercicios editables, asignación semanal.
- [x] 6. Recordatorios — `assembleDebug` correcto; AlarmManager, cola limitada, WorkManager y restauración.
- [x] 7. Reprogramación e historial — compilación y 5 tests Room/emulador correctos, incluido deshacer seguro.
- [x] 8. OCR local y revisión — compilación, 11 tests JVM y prueba de OCR con imagen real en emulador correctos; sin permiso INTERNET.
- [x] 9. Configuración — compilación y 6 pruebas Room correctas; copia/restauración atómica verificada.
- [x] 10. Pulido y verificación final — compilación, Lint, 14 pruebas JVM y 14 pruebas Android correctas.

## Entrega

- APK instalable: `dist/CampusFlow-1.0.0-debug.apk`.
- Copia de Gradle: `app/build/outputs/apk/debug/app-debug.apk` (contenido idéntico).
- Tamaño: 74.987.644 bytes (~75 MB); incluye OCR local y cuatro arquitecturas.
- Firma de desarrollo Android verificada con `apksigner` (esquema v2).
- SHA-256: `426c595f4887a005ec726e8fc588da8074ca2141ec0c23b5143226d3d866c747`.
- Proyecto listo para abrir en Android Studio; wrapper de Gradle incluido.
- No había un teléfono físico conectado mediante ADB.

## Resultados finales observados

| Comprobación | Resultado |
| --- | --- |
| `assembleDebug` | Correcto |
| `testDebugUnitTest` | 14 pruebas, 0 fallos |
| `lintDebug` | 0 errores; 17 avisos sobre versiones de dependencias y metadatos de backup |
| Instrumentación Android, ejecución completa final | `OK (14 tests)` |
| Crear, recrear, completar, deshacer y posponer en 320 dp / fuente 1,3× | Correcto |
| Pantalla 1080×2340, densidad 420 (aprox. tamaño lógico del A55) | Flujos UI y capturas claro/oscuro correctos |
| Alarma con interfaz cerrada y proceso terminado | Notificación recibida |
| Alarma tras reinicio del emulador | Notificación recibida sin abrir la app |
| Firma del APK final y coincidencia de las dos copias | Correcto |

Emulador utilizado: Android 15 / API 35, x86_64, WHPX, 2 núcleos y renderizado
por software. Las pruebas instrumentadas cubren Room, migración, copia/restauración,
OCR real de una imagen, alarmas reales, rutinas y navegación/estado Compose.

Evidencia principal:
- `verification/android-final-results.txt`
- `verification/small-screen-result.txt`
- `verification/background-result.txt`
- `verification/reboot-result.txt` y `verification/reboot.png`
- `verification/apk-signature.txt`, `apk-info.txt`, `apk-permissions.txt`
- `verification/ui/home-light.png`, `home-dark.png`, `schedule-dark.png`
- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/reports/lint-results-debug.html`

## Incidencias encontradas y tratadas

- Se corrigió un recurso de splash que necesitaba la carpeta `values-v31`.
- Un arranque inicial registró un ANR durante presión de memoria/CPU del entorno.
  Se redujo Gradle a 2 GB, se eliminó el proceso separado del compilador Kotlin
  y se desplazó la preparación de recordatorios al contexto IO. Las siguientes
  pruebas de interfaz completaron; el arranque del emulador sigue siendo lento.
- Se corrigió la recuperación de una alarma cuyo horario había pasado durante
  el reinicio. Ahora se recuperan avisos previamente programados mientras la
  actividad siga en curso, hasta tres horas desde el aviso original.
- Se ajustaron las pruebas táctiles para esperar al Snackbar y desplazar tarjetas
  fuera del área visible en pantallas pequeñas; la suite completa terminó verde.
- El script de reinicio usa espera de bloqueo SQLite y datos identificados de
  prueba para evitar competir con el mantenimiento durante su preparación.

## Límites de esta verificación

- No se probó en un Galaxy A55 físico ni en todas las versiones Android soportadas.
- OCR heurístico: tablas complejas o fotografías difíciles necesitan revisión y
  corrección manual. No incorpora un servicio de IA externo.
- El temporizador persiste, pero la finalización se confirma desde Estudio; no
  mantiene un servicio de primer plano ni emite una alarma al llegar a cero.
- Las restricciones de batería, permisos y «Forzar detención» son condiciones de
  Android; están explicadas en la app y en `USER_GUIDE.md`.
- Se entrega un APK de desarrollo. Una publicación de producción requiere firma
  privada propia y validación adicional en dispositivos físicos.
