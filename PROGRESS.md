# CampusFlow — registro de desarrollo

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
