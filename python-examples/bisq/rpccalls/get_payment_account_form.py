import os
import tempfile
from builtins import print

import grpc

# from getpass import getpass
import bisq.api.grpc_pb2 as bisq_messages
import bisq.api.grpc_pb2_grpc as bisq_service


def main():
    grpc_channel = grpc.insecure_channel('localhost:9998')
    grpc_service_stub = bisq_service.PaymentAccountsStub(grpc_channel)
    api_password: str = 'xyz'  # getpass("Enter API password: ")
    try:
        response = grpc_service_stub.GetPaymentAccountForm.with_call(
            bisq_messages.GetPaymentAccountFormRequest(payment_method_id='SWIFT'),
            metadata=[('password', api_password)])
        json_string = response[0].payment_account_form_json
        print('Response: ' + json_string)
        # The client should save this json string to file, manually fill in the form
        # fields, then use it as shown in the create_payment_account.py example.
        json_filename = os.sep.join([tempfile.gettempdir(), 'swift-account-form.json'])
        print('Write response to json file:  {0}'.format(json_filename))
        json_file = open(json_filename, "w")
        num_written_chars = json_file.write(json_string)
        print('Wrote {0} characters to {1}'.format(num_written_chars, json_file.name))
    except grpc.RpcError as rpc_error:
        print('gRPC API Exception: %s', rpc_error)


if __name__ == '__main__':
    main()
