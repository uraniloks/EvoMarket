# EvoMarket

**EvoMarket** is a client-side market tracker for the DiamondWorld PrisonEvo chat, integrated as an EvoPlus addon. It detects buy/sell messages visible to the Minecraft client and organizes offers into searchable categories.

> Unofficial community project. EvoMarket is not affiliated with or endorsed by DiamondWorld or EvoPlus.

## Downloads and Updates

The recommended place to download EvoMarket releases is [Modrinth](https://modrinth.com/mod/evomarket).

You can also find source code, release notes, and downloadable release files on [GitHub Releases](https://github.com/vanyatash4r/EvoMarket/releases).

- **Download the mod:** [Modrinth](https://modrinth.com/mod/evomarket)
- **GitHub releases:** [Releases](https://github.com/vanyatash4r/EvoMarket/releases)
- **Report a bug or suggest a feature:** [GitHub Issues](https://github.com/vanyatash4r/EvoMarket/issues)

Check the release notes before updating and use a JAR built for your Minecraft version.

## Features

- Detects trade messages such as `куплю`, `скупаю`, `продам`, and `продаю`.
- Separates buying and selling offers.
- Categorizes common PrisonEvo items, including rune cases, pet cases, Bat Treasures, nuts, lucky blocks, experience, cosmetics, and currency.
- Recognizes common abbreviations and spelling variations used in trade chat.
- Distinguishes in-game currency from LC (personal account currency) payment mentions.
- Parses common currency notation and compact exchange-rate formats.
- Preserves original chat text so offers can be interpreted in context.
- Stores a local offer history; it does not automatically buy or sell anything.

## Compatibility

- Minecraft Java Edition **26.2**
- **Fabric**
- **Java 25**
- **EvoPlus** addon API
- Fabric API

EvoPlus and Fabric API are required at runtime.

## Build on Windows

1. Install JDK 25 or newer: <https://adoptium.net/temurin/releases/?version=25>.
2. Download or clone this repository.
3. Run `BUILD.bat`. The script downloads Gradle and project dependencies on first run, so an internet connection is required.
4. Wait for `BUILD SUCCESSFUL`.
5. The output JAR is `build/libs/evomarket-0.1.13.jar`.

This repository contains source code, not a prebuilt mod JAR.

## Install

Copy `evomarket-0.1.13.jar` into the `mods` folder of your Minecraft 26.2 Fabric instance, alongside EvoPlus and Fabric API. Remove older EvoMarket JARs before installing a newer version. The addon window is opened with `Ctrl+M`.

## Known limitations

Trade chat uses free-form text, server-specific abbreviations, and nonstandard price notation. Some offers may be misclassified or have an unknown price. Please include the original chat message when reporting a parsing issue. Messages received before the scanner starts cannot be recovered.

## Reporting bugs

When reporting an issue, include:

- EvoMarket version
- Minecraft, Fabric Loader, Fabric API, and EvoPlus versions
- The original chat message (remove any private information)
- A screenshot or relevant log excerpt
- What category/result was expected

## AI assistance disclosure

AI tools were used to assist with portions of the source code, project documentation, and parsing tests. The project is still under active development, and its results should be tested against real DiamondWorld chat messages.

## License and usage

Copyright (c) 2026 uraniloks. All rights reserved. No permission is granted to copy, modify, redistribute, or publish modified versions of this source without prior written permission from the author.
