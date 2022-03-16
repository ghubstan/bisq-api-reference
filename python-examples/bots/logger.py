import logging

logging.basicConfig(
    format='[%(asctime)s %(levelname)s %(threadName)s] %(message)s',
    level=logging.INFO,
    datefmt='%H:%M:%S')
log = logging.getLogger()
