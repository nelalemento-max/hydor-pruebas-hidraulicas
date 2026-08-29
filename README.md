# HYDOR Pruebas Hidráulicas

Aplicación Android para trabajo de campo en pruebas hidráulicas de redes de agua potable.

## Primera versión

- Funcionamiento offline.
- Usuarios y operador responsable.
- Proyectos, baterías/sectores y tramos entre llaves de paso.
- Longitud, diámetro y presión objetivo por tramo.
- Cronómetro configurable de ensayo.
- Lecturas iniciales, intermedias y finales.
- Fotografía del manómetro con fecha y hora.
- Reconocimiento local de la lectura del manómetro y confirmación/corrección obligatoria por el usuario.
- Curva presión vs. tiempo.
- Criterios configurables de aceptación y alertas por posible fuga.
- Informe individual por tramo o consolidado de múltiples pruebas.
- PDF, exportación de datos y respaldo local.

## Tecnología

Kotlin, Jetpack Compose, Room/SQLite, CameraX y procesamiento local de imágenes.

## Principio de seguridad técnica

La lectura detectada por cámara será una asistencia. La presión que se incorpora al registro técnico será siempre la lectura confirmada por el operador.

## Estado

Estructura Android inicial creada. Siguiente etapa: navegación, formularios de proyecto/tramo, DAO de Room y flujo de ensayo.
