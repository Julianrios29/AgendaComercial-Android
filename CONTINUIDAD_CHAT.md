# Continuidad del proyecto Pà Solà Comercial

Estado del proyecto al cerrar este chat.

## Repositorio y versión
- Repositorio: Julianrios29/AgendaComercial-Android
- Rama principal: main
- applicationId: com.pasola.agendacomercial
- namespace: com.agendacomercial.app
- Última versión compilada en main: 0.16.7
- Android: Kotlin + Jetpack Compose + SQLiteOpenHelper
- compileSdk/targetSdk 35, minSdk 23
- JDK 17, Gradle 8.9

## Funciones principales ya implementadas
- Agenda semanal con clientes y prospecciones.
- Fichas de clientes y prospecciones con foto, contacto, teléfono, dirección, observaciones, etc.
- Nueva prospección unificada: datos + conversación + necesidades + próximos pasos + notas en una sola pantalla.
- Las nuevas prospecciones guardadas cuentan automáticamente como realizadas en Resumen.
- Pantalla Sugerencias después de visitas/prospecciones, con clientes cercanos y búsquedas comerciales por zona.
- Resumen por periodos: día, 2 días, semana, mes y rango personalizado.
- Exportación Excel de visitas según el periodo seleccionado.
- Presupuestos desde fichas de cliente/prospección.
- Catálogo de productos Pà Solà con unidades por caja y descripciones.
- Presupuesto Excel con precio por unidad, número de cajas, unidades por caja, códigos, importes y fotos de producto.
- Los códigos y precios introducidos se recuerdan mediante SharedPreferences.

## Estado actual del presupuesto
El cálculo debe mantenerse como:
importe = cajas × unidades por caja × precio por unidad.

El usuario confirmó previamente que:
- Excel Android abre correctamente el presupuesto en versiones anteriores.
- Las fotos de producto se descargaban bien desde la web.
- Los códigos y precios se conservaban bien.
- El precio debe ser siempre precio por unidad, no precio por caja.

## Problema pendiente IMPORTANTE
Después de los últimos intentos de corregir el logo:
1. El Excel muestra “no se puede mostrar la imagen” y ya no aparecen correctamente las imágenes.
2. El logo situado a la derecha en la pantalla Agenda tampoco se ve.
3. El problema apareció durante los cambios del recurso app_logo / lógica de conversión de logo.
4. Antes de esos cambios, las fotos de producto del presupuesto sí funcionaban.
5. Hay que revisar BudgetXlsxExporter.kt y los recursos gráficos de res/drawable y mipmap.
6. No seguir redimensionando el mismo recurso a ciegas: comprobar realmente el archivo binario y su formato antes de incrustarlo.
7. Mantener intacta la lógica de precio por unidad y las fotos de producto que ya funcionaban.

## Archivos clave
- app/src/main/java/com/agendacomercial/app/CrmApp.kt
- app/src/main/java/com/agendacomercial/app/AppViewModel.kt
- app/src/main/java/com/agendacomercial/app/Data.kt
- app/src/main/java/com/agendacomercial/app/BudgetCatalog.kt
- app/src/main/java/com/agendacomercial/app/BudgetXlsxExporter.kt
- app/src/main/java/com/agendacomercial/app/DailyVisitsXlsxExporter.kt
- app/src/main/AndroidManifest.xml
- app/src/main/res/drawable/app_logo.png
- app/src/main/res/mipmap-xxxhdpi/pa_sola_app.png
- app/src/main/res/mipmap-xxxhdpi/pa_sola_legacy.png
- app/build.gradle.kts
- .github/workflows/build-apk.yml

## Siguiente paso recomendado
Empezar inspeccionando los recursos gráficos reales empaquetados en el APK 0.16.7 y compararlos con una versión anterior donde las imágenes funcionaban. Después corregir el logo sin tocar la descarga/incrustación de fotos de producto.
