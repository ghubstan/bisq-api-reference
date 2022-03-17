from setuptools import setup, find_packages

setup(
    name='python-examples',
    version='0.0.1',
    description='Bisq Python Examples',
    url='https://bisq-network.github.io/slate/#python-examples',
    license='GNU AFFERO GENERAL PUBLIC LICENSE Version 3',
    setup_requires=['wheel'],
    # packages=find_packages(include=['rpccalls', 'bots', 'bots.*']),
    packages=find_packages(),
    # package_data={'bots': ['bots/assets/*.png']},
    install_requires=['grpcio', 'grpcio-tools', 'mypy-protobuf', 'python-examples'],
    classifiers=[
        'Operating System :: POSIX :: Linux :: MacOS',
        'Programming Language :: Python :: 3.8.10',
    ],
)
