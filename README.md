# Network Monitoring System

## What It’s All About

The Network Monitoring System is a tool I built to keep track of devices in a network. It’s simple, reliable, and designed to discover devices, monitor their performance, and keep data secure. I created it using Java and Go to make network monitoring straightforward and effective.

## What It Does

- **Safe Credentials**: Create credential profiles, securely stored with AES encryption and unique IVs for top-notch protection.
- **Device Discovery**: Scans your network using IP, port, and SSH checks to identify devices.
- **Device Monitoring**: Set discovered devices to poll at interval you choose, with metrics stored in a database for tracking changes.
- **Metric Gathering**: The Go plugin collects device stats and securely sends them to the Java backend for storage and analysis.
- **Detailed Insights**: APIs provide access to past polling data, device availability percentages, and full CRUD operations for all features.
- **Device History**: unprovisioned devices retain their records. If they reappear, they’re automatically linked to their past data.
- **Instant Start**: Polling kicks off for provisioned devices as soon as the system starts.

## How It Works

1. **Create Credentials**: Set up a credential profile, which is encrypted for security.
2. **Find Devices**: Add a discovery profile, mapped to a credential profile, to scan and discover devices.
3. **Monitor Devices**: Provision discovered devices to start polling at your chosen interval.
4. **Collect Data**: Metrics are gathered by the Go plugin, sent to the Java backend, and stored in a database.
5. **View Results**: Use APIs to access polling history, availability stats, and other device details.

## Keeping It Secure

- Credentials are locked down with AES encryption and unique IVs, ensuring no two passwords produce the same encrypted output.
- Data transferred between the Go plugin and Java backend is encrypted for safety.

## The Tech

- **Java**: Runs the core system, manages APIs, and handles database storage.
- **Go Plugin**: Powers SSH-based discovery and metric collection.
