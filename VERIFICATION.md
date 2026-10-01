# Plan de verificación

## Resultado actual — Kairos, fase 1

Build y Lint correctos; 19 tests JVM y 19 tests Android pasan en emulador API 35.
Lint: 0 errores y 16 avisos exclusivamente de versiones de dependencias.
Detalle de cambios, comando de ejecución, APK y límites en `PROGRESS.md`.

Regresiones incorporadas:
- Generación concurrente con edición de una serie; rechazo del editor obsoleto.
- Pausa del timer al editar horario y descarte al cambiar categoría.
- Deshacer no sobrescribe una edición posterior.
- Copias con fechas/timers inválidos conservan datos y temporizador existentes.
- La alarma real sigue llegando después de rechazar una restauración inválida.
- Hora local en dos zonas, hueco y solapamiento DST; reglas semanales inválidas.
- OCR con acentos Unicode descompuestos y minutos malformados.
- Semántica Compose de interruptor y día completo seleccionado.

Los escenarios de reinicio/proceso terminado y tamaños especiales descritos
más abajo pertenecen a verificaciones históricas; no se repitieron en esta fase.

Cada fase requiere `assembleDebug` antes de avanzar. Las pruebas de lógica se
ejecutan con `testDebugUnitTest`; Room y la integración Android se validan con
`connectedDebugAndroidTest` cuando hay dispositivo o emulador disponible.

## Casos críticos

1. Crear una materia y una clase semanal; cerrar y abrir: persiste.
2. Recurrencia: lunes a domingo, cambio de mes/año y fechas con cambio horario.
3. Completar una ocurrencia: las demás permanecen pendientes. Deshacer restaura.
4. Posponer estudio/entreno: conserva origen, cambia agenda y alarma; deshacer
   restaura fecha y estado sin modificar las demás ocurrencias.
5. Editar una serie: avisar del alcance; conservar completados y pospuestos.
6. Temporizador: iniciar, pausar, recrear pantalla/proceso, finalizar una sola vez.
7. Rutina: crear, editar ejercicios, asignar al calendario, completar y registrar.
8. Permisos de notificación y exactitud: denegar, conceder y volver a la app.
9. Alarma con aplicación cerrada; restauración tras reinicio/cambio de hora.
10. OCR sin conexión: elegir imagen, reconocer, corregir, eliminar candidato,
    agregar faltantes y guardar sólo tras confirmación explícita.
11. Tema claro/oscuro/sistema; preferencia conservada tras reinicio.
12. Pantalla pequeña, teclado, fuente ampliada y bordes del sistema (edge-to-edge).

## Condiciones de Android

- Cerrar la pantalla o quitar la app de recientes no equivale a «Forzar
  detención». Android bloquea alarmas y trabajos de una app forzada a detenerse
  hasta que el usuario la abre nuevamente.
- Sin acceso a alarmas exactas se utiliza la alternativa inexacta de Android;
  Doze y las políticas del fabricante pueden retrasarla.
- El temporizador registra el tiempo mediante un instante persistido; el contador
  visible se reconstruye al volver. No necesita mantener la pantalla encendida.

Los resultados reales se documentan al finalizar en `PROGRESS.md`.

## Cobertura histórica de la base original (ampliada arriba)

- 14 pruebas JVM: calendario, límites de recurrencia, DST, reconstrucción de
  temporizador, recuperación de alarmas tras reinicio e interpretación OCR en
  líneas y columnas, AM/PM y ambigüedad.
- 8 pruebas Room: completar/deshacer, cancelación sin regeneración, temporizador,
  traslados, deshacer obsoleto, copias atómicas, versiones de series y edición de
  una excepción sin perder el historial.
- 1 migración: esquema 1 → 2 conservando datos.
- 1 OCR Android: reconocimiento real de una imagen sintetizada y verificación de
  que no se guardan actividades ni existe permiso INTERNET.
- 1 integración de alarma: entrega Android real, deduplicación, reprogramación,
  rechazo de alarma obsoleta y cancelación al completar.
- 3 pruebas Compose: crear/recrear/completar/deshacer/posponer; crear rutina y
  asignarla semanalmente; persistencia del tema y capturas claro/oscuro/horario.

Las pruebas de UI esperan a que finalice el Snackbar antes de pulsar un botón
que pueda quedar temporalmente bajo él. En pantalla pequeña desplazan la lista
para alcanzar tarjetas fuera del área visible.
