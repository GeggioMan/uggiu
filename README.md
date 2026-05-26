# uggiu - Heart Rate Monitor

**uggiu** è un'applicazione Android moderna progettata per il monitoraggio della frequenza cardiaca in tempo reale utilizzando smartband Bluetooth Low Energy (BLE), come la Xiaomi Smart Band 9 o le Huawei Band.

## 🚀 Funzionalità Principali

*   **Monitoraggio Real-time**: Visualizzazione istantanea dei battiti per minuto (BPM) con un'icona del cuore animata che segue il ritmo cardiaco.
*   **Grafico Dinamico**: Visualizzazione dell'andamento cardio con scale temporali selezionabili (1h, 4h, 8h, 12h).
*   **Sistema di Allarme Personalizzabile**:
    *   Imposta una soglia massima di BPM (es. 120).
    *   Imposta una durata minima (es. 10 secondi) sopra la soglia prima che l'allarme scatti.
    *   Scegli tra allarme sonoro o notifica con vibrazione.
*   **Cronologia Sessioni**: Salvataggio automatico di ogni allenamento nel database locale (Room).
*   **Auto-Save**: Salvataggio dei dati ogni 30 secondi per prevenire la perdita di informazioni in caso di chiusura improvvisa.
*   **Esportazione Dati**: Possibilità di esportare ogni sessione in formato CSV per analisi esterne.
*   **Supporto Background**: Grazie a un *Foreground Service*, l'app continua a monitorare il battito e gli allarmi anche se lo schermo è spento o l'app è in background.
*   **Riconnessione Automatica**: Se il segnale Bluetooth viene perso durante una sessione, l'app tenta automaticamente il ripristino del collegamento.
*   **Badge Batteria**: Visualizzazione del livello di carica della band direttamente nella dashboard (se supportato dal dispositivo).

## 🛠 Tech Stack

*   **Linguaggio**: Kotlin
*   **UI**: Jetpack Compose (Material 3)
*   **Database**: Room (per la persistenza delle sessioni)
*   **Networking**: Bluetooth Low Energy (BLE) API
*   **Architettura**: MVVM pattern con utilizzo di Foreground Services per il monitoraggio continuo.

## 📦 Installazione e Configurazione

### Requisiti
*   Dispositivo Android con versione 8.0 (API 26) o superiore.
*   Permessi necessari: Bluetooth, Posizione (per scansione BLE), Sensori del corpo e Notifiche.

### Configurazione Smartband
Per far sì che l'app rilevi la tua band:
1.  Sulla band, vai in **Impostazioni > Condividi FC** (o *Heart Rate Broadcast*).
2.  Attiva l'opzione di trasmissione.
3.  Assicurati che il Bluetooth del telefono sia attivo.
4.  Apri **uggiu** e premi **AVVIA**.

## 📂 Struttura del Progetto

*   `MainActivity.kt`: Punto di ingresso dell'interfaccia utente e gestione della UI state.
*   `service/WorkoutService.kt`: Gestisce la logica di business in background e le notifiche di sistema.
*   `ble/BLEHeartRateClient.kt`: Gestore della comunicazione Bluetooth e parsing dei dati GATT.
*   `data/`: Definizione del database Room, DAO e dell'entità `WorkoutSession`.

## 📄 Licenza

Questo progetto è distribuito ad uso personale e dimostrativo.
