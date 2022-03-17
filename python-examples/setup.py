from setuptools import setup, find_packages

setup(
    name='bisq',
    version='0.0.1',
    description='Bisq Python Examples',
    url='https://bisq-network.github.io/slate/#python-examples',
    license='GNU AFFERO GENERAL PUBLIC LICENSE Version 3',
    setup_requires=['wheel'],
    packages=find_packages(),
    include_package_data=True,
    package_data={'bisq.bots': ['assets/*.png']},
    install_requires=['grpcio', 'grpcio-tools', 'mypy-protobuf', 'bisq'],
    classifiers=[
        'Operating System :: POSIX :: Linux :: MacOS',
        'Programming Language :: Python :: 3.8.10',
    ],
)
