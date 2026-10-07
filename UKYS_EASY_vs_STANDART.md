# Сравнение сборок: «UKYS EASY» и «UKYS STANDART 0.93»

- A = `C:\Users\egord\AppData\Roaming\com.noctrinth.app\profiles\UKYS EASY`
- B = `C:\Users\egord\AppData\Roaming\com.noctrinth.app\profiles\UKYS STANDART 0.93`

## Итог

**EASY — это STANDART 0.93 с тремя отключёнными модами.** Больше различий нет: конфиги,
скрипты, ресурспаки, шейдерпаки, подпапки `mods/` и `options.txt` совпадают байт в байт.

Моды в EASY отключены переименованием файла: `.jar` → `.jar---`. Такой файл Forge не
загружает. Содержимое jar при этом то же самое (SHA-1 совпадают), то есть это не другие
версии, а те же моды, выключенные.

## Моды

В обеих сборках в корне `mods/` по 87 файлов; 84 совпадают полностью.

| Мод | UKYS EASY | UKYS STANDART 0.93 | SHA-1 (одинаковый) |
|---|---|---|---|
| No Sleep | `nosleepmod.jar---` — **выключен** | `nosleepmod.jar` — включён | `8db5e8deaa81…` |
| Epic Siege Mod 10.0.148 | `EpicSiegeMod-10.0.148.jar---` — **выключен** | `EpicSiegeMod-10.0.148.jar` — включён | `3d93fc59f298…` |
| Special Mobs 3.7.0 | `SpecialMobs-3.7.0.jar---` — **выключен** | `SpecialMobs-3.7.0.jar` — включён | `7b615382d93c…` |

Подпапки `mods/1.7.10/`, `mods/carpentersblocks/`, `mods/mcheli/` — идентичны.

## Остальное

| Что | A | B | Различий |
|---|---|---|---|
| `config/` | 1450 файлов | 1450 файлов | 0 |
| `scripts/` | 1 | 1 | 0 |
| `resourcepacks/` | 3513 | 3513 | 0 |
| `shaderpacks/` | 505 | 505 | 0 |
| `datapacks/` | 0 | 0 | 0 |
| `options.txt` | — | — | 0 |

Следствие: конфиги Epic Siege Mod и Special Mobs в EASY лежат такие же, как в STANDART,
просто не читаются, пока моды выключены. Если включить их обратно, EASY станет полной копией
STANDART.

Не сравнивались (это не часть сборки, а данные игрока): `saves/`, `screenshots/`,
`crash-reports/`, `command_history.txt`.
