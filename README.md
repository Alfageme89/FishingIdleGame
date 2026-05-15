# 🎣 Fishing Idle Game

Un juego idle de pesca para Android con biomas, jefes y ranking online.

[![Build Debug APK](https://github.com/Alfageme89/FishingIdleGame/actions/workflows/build-apk.yml/badge.svg)](https://github.com/Alfageme89/FishingIdleGame/actions/workflows/build-apk.yml)

## 📥 Descargar APK

> **La APK se genera automáticamente con cada commit en `main`.**

1. Ve a la pestaña **[Actions](https://github.com/Alfageme89/FishingIdleGame/actions/workflows/build-apk.yml)**
2. Abre el último workflow **Build Debug APK** ✅
3. Baja hasta **Artifacts** y descarga `app-debug`
4. Descomprime el `.zip` y instala el `.apk` en tu Android

> ⚠️ Necesitas tener activada la opción **"Instalar desde fuentes desconocidas"** en tu móvil.

---

## 🎮 Cómo jugar

### Pesca
- **Toca** la pantalla para lanzar el anzuelo hacia abajo
- Los peces aparecen según la profundidad — cuanto más bajas, mejores peces
- El anzuelo sube solo cuando llenas el peso máximo o pulsas **Recoger**
- **Al subir puedes seguir pescando** — los peces aparecen por delante del anzuelo

### Biomas
| Bioma | Profundidad | Dificultad |
|-------|-------------|------------|
| Lago Sereno | 0 – 100 m | ⭐ |
| Arrecife Coral | 100 – 300 m | ⭐⭐ |
| Océano Profundo | 300 – 800 m | ⭐⭐⭐ |
| Abismo Abisal | 800 – 2000 m | ⭐⭐⭐⭐ |
| Zona Volcánica | 2000 m+ | ⭐⭐⭐⭐⭐ |

Desbloqueas el siguiente bioma **derrotando al jefe** del actual.

### Jefes
- Al llegar a cierta profundidad aparece un **aviso** y comienza la pelea
- Aparece una **barra de tensión** con una zona verde que se mueve
- **Toca** cuando el marcador esté en la zona verde para hacer daño
- Si fallas demasiados clicks fuera de la zona verde → **HAS FRACASADO**
- Al ganar pasas automáticamente al siguiente bioma

### Mejoras (Tienda)
- **Profundidad** — llega más abajo
- **Capacidad** — sube más peces de una vez
- **Giro** — controla mejor el anzuelo horizontalmente
- **Turbo** — sube más rápido al recoger
- **Estabilidad** — más margen de error en las peleas de jefes
- **Cebo** — daño extra al jefe

### Prestigio
- Cuando tengas suficientes puntos aparece el botón **Prestigio** (⚡)
- Reinicia tu partida pero con un **multiplicador permanente de puntos**
- En el ranking global, **el nivel de prestigio tiene prioridad** sobre los puntos

### Colección
- Cada pez tiene talla: Pequeño / Mediano / Adulto / Anciano
- Tu **peso máximo** por especie se guarda en el ranking online

---

## 🏆 Ranking
El juego conecta con Firebase. El ranking ordena por:
1. **Nivel de Prestigio** (mayor = arriba)
2. **Puntuación** (desempate)

---

## 🛠️ Tecnologías
- Kotlin + Jetpack Compose
- Firebase Firestore (ranking online)
- GitHub Actions (CI/CD → APK automática)
