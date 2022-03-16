# Authentication

An API password option `--apiPassword=<api-password>` is passed in the daemon's start command.

All API client requests must authenticate to the daemon with the api-password.

* Each CLI command must include the `--password=<api-password>` option.

* API authentication from Java and Python requests are demonstrated in API usage examples.
