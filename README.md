# uggiu - Heart Rate & Crisis Monitor

**uggiu** è un'applicazione Android specializzata nel monitoraggio della frequenza cardiaca in tempo reale e nella rilevazione automatica di situazioni critiche utilizzando smartband Bluetooth Low Energy (BLE).

## 🚀 Funzionalità Principali

*   **Discovery Automatica (Zero-Click)**: L'app avvia la ricerca dei dispositivi appena viene aperta o torna in primo piano. Basta selezionare la propria band dalla lista per iniziare.
*   **Monitoraggio Crisi Avanzato**:
    *   **Rilevazione Intelligente**: La crisi viene registrata solo se il battito supera la soglia (X) per una durata minima (Y).
    *   **Contatore Real-time**: Un cronometro in tempo reale mostra la durata della crisi attiva, includendo retroattivamente il tempo di osservazione iniziale.
    *   **Notifiche e Allarmi**: Feedback sonoro o vibrazione persistente durante gli eventi critici, anche in background.
*   **Grafico Dinamico (Sliding Window)**: Visualizzazione dell'andamento cardio con scale temporali (1h, 4h, 8h, 12h) che scorrono mantenendo la proporzione temporale corretta.
*   **Cronologia degli Eventi**: Database locale dedicato alla registrazione delle singole crisi (inizio, fine, durata e picco BPM).
*   **Diagnostica Dispositivo**: 
    *   Visualizzazione Nome e MAC Address del sensore connesso.
    *   Badge Batteria e Indicatore di Prossimità (RSSI) per monitorare la qualità del segnale.
    *   Riconnessione Automatica intelligente in caso di perdita segnale.
*   **Rilevamento Contatto**: Gestione avanzata dell'assenza di segnale (band non indossata) tramite flag BLE ufficiali per evitare "battiti fantasma".

## 🛠 Tech Stack

*   **Linguaggio**: Kotlin 2.x
*   **UI**: Jetpack Compose (Material 3) con architettura reattiva.
*   **Database**: Room con supporto KSP (Tabella `CrisisRecord` per la cronologia).
*   **Networking**: Android Bluetooth LE API (Standard GATT Heart Rate Service).
*   **Background**: Foreground Service con notifica persistente per monitoraggio H24.

## 📦 Installazione e Distribuzione

### Generazione APK
È possibile generare l'eseguibile aggiornato direttamente tramite Gradle utilizzando il task personalizzato:
```bash
./gradlew generateUggiuApk
```
L'APK sarà disponibile in: `apk/uggiu.apk`

### Requisiti
*   Android 8.0 (API 26) o superiore.
*   Permessi: Bluetooth (Scan/Connect), Sensori del corpo, Notifiche.

## 📂 Struttura del Progetto

*   `MainActivity.kt`: Dashboard interattiva, gestione permessi e navigazione cronologia.
*   `service/WorkoutService.kt`: Core engine per la registrazione dati e logica degli allarmi.
*   `ble/BLEHeartRateClient.kt`: Gestore GATT a basso livello con parsing dei pacchetti e gestione RSSI.
*   `data/`: Persistenza dati con Room (`CrisisRecord` e `WorkoutSession`).

## 📄 Licenza

Questo progetto è distribuito ad uso personale e dimostrativo.
