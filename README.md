# CampusFlow

Organizador Android local de universidad, estudio y entrenamiento. Kotlin,
Jetpack Compose, Material 3, Room, ViewModel, Flow y Navigation Compose.

## Funciones implementadas

- Dashboard con agenda de hoy, pendientes anteriores y progreso semanal.
- Horario lunes–domingo y agenda, actividades únicas/diarias/semanales, materias,
  notas, fechas, duración u hora de fin y recordatorios configurables.
- Completar, eliminar y posponer con deshacer; historial y origen conservados.
- Edición individual y de próximas ocurrencias de una serie sin cambiar el pasado.
- Materias, sesiones de estudio y temporizador persistente (iniciar/pausar/finalizar).
- Rutinas y ejercicios editables con series, repeticiones, descanso, peso y notas;
  asignación semanal y registro de entrenamientos completados.
- Notificaciones reales con AlarmManager, mantenimiento con WorkManager,
  restauración tras reinicio y gestión de permisos.
- OCR latino empaquetado con ML Kit; revisión editable obligatoria antes de importar.
- Claro/oscuro/sistema, colores dinámicos opcionales y preferencias persistentes.
- Exportar/restaurar datos mediante archivos JSON, con restauración transaccional.

**Guía de instalación y uso:** [`USER_GUIDE.md`](USER_GUIDE.md).

## Compilar en esta computadora

Las herramientas descargadas están aisladas en `.toolchain/`.

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
powershell -ExecutionPolicy Bypass -File .\build.ps1 -Tasks testDebugUnitTest
```

APK de desarrollo: `app/build/outputs/apk/debug/app-debug.apk`.
`build.ps1` deja también una copia lista para transferir en
`dist/CampusFlow-1.0.0-debug.apk` y su huella SHA-256.

Es un APK universal de prueba firmado con la clave de desarrollo, compatible con
ARM64 (Galaxy A55), ARMv7 y emuladores x86/x86_64. No necesita conexión para
consultar/editar datos ni para reconocer texto de imágenes.

En otra computadora: abrir esta carpeta con Android Studio, instalar SDK 35 y
usar JDK 17 o 21. Dejar que Android Studio genere `local.properties` con su SDK.
El wrapper de Gradle permite ejecutar `./gradlew assembleDebug`.

## Decisiones de producto y arquitectura

- Navegación inferior: Inicio, Horario, Estudio y Entreno. Configuración arriba.
- Datos y fotos tratados localmente. OCR latino empaquetado con el APK.
- Room es la fuente de verdad; UI observable con Flow y ViewModels.
- Las series recurrentes y sus ocurrencias se separan: mover o completar una
  ocurrencia no altera el resto del horario. Cada traslado conserva su historia.
- AlarmManager para avisos puntuales y WorkManager para mantenimiento y
  recuperación. Las alarmas exactas dependen del permiso del sistema; la UI
  expone su estado y el acceso a los ajustes Android.
- El intérprete de texto del OCR tiene una interfaz reemplazable, con revisión
  obligatoria antes de guardar. Las tablas ambiguas necesitan corrección manual.

## Estado de verificación

Consultar `PROGRESS.md` para las compilaciones, pruebas y limitaciones observadas.

```powershell
# Desde PowerShell, dentro de esta carpeta:
.\build.ps1 -Tasks @('assembleDebug', 'testDebugUnitTest', 'lintDebug')
# Con un emulador o dispositivo de pruebas conectado:
.\build.ps1 -Tasks connectedDebugAndroidTest
```

Las pruebas instrumentadas crean datos de prueba y los limpian; se ejecutaron en
el emulador propio del proyecto. `tools/verify-background.ps1` está restringido a
ese emulador y comprueba entrega tras terminar el proceso; `-Reboot` comprueba
restauración después de reiniciarlo. Las pruebas de interfaz generan capturas
en `Pictures/CampusFlowVerification` del emulador.

## Dónde estudiar el código

- `data/Entities.kt`, `CampusDao.kt`: modelo y consultas Room.
- `data/CampusRepository.kt`: recurrencias, excepciones, completar y traslados.
- `data/StudyRepository.kt`, `WorkoutRepository.kt`: estudio y rutinas.
- `domain/`: cálculo de fechas y temporizador, probado sin Android.
- `ui/`: pantallas Compose, componentes reutilizables y estado de ViewModel.
- `reminders/`: alarmas, notificaciones y trabajo persistente.
- `ocr/`: reconocimiento on-device e intérprete sustituible.
- `app/schemas/`: versiones exportadas de Room y migración 1 → 2.

La distribución de producción necesita una clave de firma privada propia; este
proyecto entrega la variante de desarrollo para instalación y pruebas personales.
