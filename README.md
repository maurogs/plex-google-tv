# PlexLite

Cliente nativo y ligero de Plex para Google TV / Android TV (Kotlin + Jetpack Compose for TV + Media3/ExoPlayer).

## Qué hace distinto respecto a la app oficial

- **Interfaz nativa** (Compose, compilada con R8), sin web view.
- **Direct play de verdad**: declara al servidor todos los códecs que el dispositivo decodifica por hardware
  (se detectan en el arranque) y todos los formatos de audio que ExoPlayer + FFmpeg pueden decodificar
  (AAC, AC3, E-AC3, DTS, TrueHD, FLAC, Opus…), además de los subtítulos SRT/ASS/PGS/VobSub. El servidor deja de
  transcodificar sin motivo.
- **Arranque rápido**: sin prueba de velocidad previa; empieza en cuanto hay ~1,5 s de búfer y sigue cargando
  hasta 2 minutos por delante.
- **Recuperación automática**: si el direct play falla a mitad de reproducción, cambia a transcodificación HLS
  desde el mismo punto.
- **Conexión al servidor**: prueba todas las conexiones directas en paralelo y elige la mejor; el relay de Plex
  (~2 Mbps) solo se usa como último recurso, y en ese caso fuerza calidad baja.
- Saltar intro / créditos (botón OK), siguiente episodio automático, reanudar, progreso sincronizado con Plex,
  subtítulos externos (.srt) en direct play, selección de pistas de audio y subtítulos.

## Compilar

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:assembleRelease
```

APK resultante: `app/build/outputs/apk/release/app-release.apk` (firmada con la clave de depuración, para instalación manual).

Usa siempre la versión `release` en la TV: la versión `debug` de Compose es notablemente más lenta.

## Instalar en la TV

1. En la TV: **Ajustes → Sistema → Información → Compilación del SO**, pulsa 7 veces para activar las opciones de desarrollador.
2. **Ajustes → Sistema → Opciones de desarrollador**: activa **Depuración USB** y **Depuración inalámbrica**.
3. Dentro de *Depuración inalámbrica* → *Vincular dispositivo con código*: verás una IP:puerto y un código.

```sh
ADB=/opt/homebrew/share/android-commandlinetools/platform-tools/adb
$ADB pair IP_DE_LA_TV:PUERTO_DE_VINCULACION     # introduce el código
$ADB connect IP_DE_LA_TV:PUERTO_DE_DEPURACION   # el puerto que aparece en la pantalla principal de Depuración inalámbrica
$ADB install -r app/build/outputs/apk/release/app-release.apk
```

La app aparece como **PlexLite** en la fila de apps.

Para ver logs si algo falla: `$ADB logcat | grep -iE 'plexlite|exoplayer|media3'`.
