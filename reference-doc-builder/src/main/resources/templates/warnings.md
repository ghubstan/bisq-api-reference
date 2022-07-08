# Warning

Never run an API Daemon and the Bisq desktop application on the same host at the same time.

The API daemon and the GUI share the same default wallet and connection ports. Beyond inevitable failures due to
fighting over the wallet and ports, doing so will probably corrupt your wallet. Before starting the API daemon, make
sure your GUI is shut down, and vice-versa. Please back up your mainnet wallet early and often with the GUI.
