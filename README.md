# Device Lock - Minecraft Fabric Mod

 **Loader**: Fabric

## Overview

Device Lock is a client-server Fabric mod that implements device-level banning. The client collects hardware device identifiers and reports them to the server, which can ban by device ID. Banned devices are denied access to the server. Virtual machine detection is also included — VM clients are rejected outright.

## Installation

1. Both client and server require **Fabric Loader 0.16+** and **Fabric API**.
2. Place `device-lock-x.x.x.jar` into the `mods/` folder on both client and server.
3. Launch the game / server.

> Note: This is a required mod on both sides. Clients without the mod will be denied connection.

## Server Admin Commands

All commands require permission level 3 (OP).

### 1. Ban a device
```
/device lock <deviceId> [reason] [duration]
```
- `deviceId`: Device ID (32-character hex string)
- `reason`: Ban reason (optional, supports multiple words; wrap in quotes for spaces)
- `duration`: Ban duration in days (optional; a trailing number is auto-detected; omit for permanent)

Examples:
```
/device lock ABC123DEF456                      # permanent ban, no reason
/device lock ABC123DEF456 cheating             # permanent ban with reason
/device lock ABC123DEF456 using xray 30        # 30-day ban with reason
/device lock ABC123DEF456 "using X-Ray" 7      # 7-day ban, reason with spaces in quotes
```

### 2. Unban a device
```
/device unlock <deviceId>
```

### 3. Look up device ID by player UUID
```
/device client <UUID>
```
Returns the most recent device ID used by the given UUID, along with ban status.

### 4. Check device login history
```
/device check <deviceId>
```
Lists all players who have logged in from this device (name, UUID, last login time), plus the device's ban status.

### 5. List all devices
```
/device list
```
Lists all known device IDs, ban status, and associated player count.

## Data Storage

Server data is stored under `mods/DeviceLock/`:
- `bans.json` — Ban records (device ID, reason, ban time, expiry time)
- `devices.json` — Device-to-player mapping (device ID, player UUID, player name, last login time)

## Client Device Identifier Collection

The client collects the following hardware information and generates a 32-character device ID via SHA-256:
- Physical network interface MAC addresses
- CPU information (brand, model)
- Motherboard / system serial number
- Operating system and architecture

The device ID is cached locally to ensure stability across sessions.

## Virtual Machine Detection

Detection heuristics (any positive match triggers VM classification):
- CPU brand string contains VMware / VirtualBox / QEMU / KVM / Hyper-V / Xen / Parallels, etc.
- MAC address OUI belongs to a known VM vendor
- System product name / BIOS vendor contains virtualization indicators
- OS-specific signals (Linux hypervisor flag, macOS ioreg, Windows registry)

## Disconnect Messages

- **Virtual machine**: "This server prohibits virtual machines"
- **Banned device**: Shows ban reason, ban time, and expiry time
- **Mod not installed**: Prompts the user to install Device Lock
