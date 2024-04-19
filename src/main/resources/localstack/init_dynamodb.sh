#!/bin/sh

table_name="testTable"
hash_key="Id"

# Create DynamoDB table, not used because we create it via AWS SDK
#awslocal dynamodb create-table \
#     --table-name "$table_name" \
#     --key-schema AttributeName="$hash_key",KeyType=HASH \
#     --attribute-definitions AttributeName="$hash_key",AttributeType=S \
#     --billing-mode PAY_PER_REQUEST

# echo "DynamoDB table '$table_name' created successfully with hash key '$hash_key'"
echo "Executed init_dynamodb.sh"