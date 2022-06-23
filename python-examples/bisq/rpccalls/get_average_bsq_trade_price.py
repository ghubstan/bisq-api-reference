import grpc

# from getpass import getpass
import bisq.api.grpc_pb2 as bisq_messages
import bisq.api.grpc_pb2_grpc as bisq_service
import grpc


def main():
    grpc_channel = grpc.insecure_channel('localhost:9998')
    grpc_service_stub = bisq_service.PriceStub(grpc_channel)
    api_password: str = 'xyz'  # getpass("Enter API password: ")
    try:
        response = grpc_service_stub.GetAverageBsqTradePrice.with_call(
            bisq_messages.GetAverageBsqTradePriceRequest(days=30),
            metadata=[('password', api_password)])
        price = response[0].price
        print('Response: ' + '30-day BTC price: ' + price.btc_price + '  30-day USD price: ' + price.usd_pricez)
    except grpc.RpcError as rpc_error:
        print('gRPC API Exception: %s', rpc_error)


if __name__ == '__main__':
    main()
