#!/bin/bash

BASE_URL="http://localhost:8080/api"

echo "=== Testing L1 and L2 Cache ==="

echo "1. Getting user by ID (first time - should hit database)"
curl -X GET "$BASE_URL/users/1" | jq '.'

echo "2. Getting same user by ID (second time - should hit L1 cache)"
curl -X GET "$BASE_URL/users/1" | jq '.'

echo "3. Getting users older than 30 (first time - should hit database)"
curl -X GET "$BASE_URL/users/older-than/30" | jq '.'

echo "4. Getting same query (second time - should hit Spring cache)"
curl -X GET "$BASE_URL/users/older-than/30" | jq '.'

echo "5. Getting products by user ID (first time - should hit database)"
curl -X GET "$BASE_URL/products/user/1" | jq '.'

echo "6. Getting same products (second time - should hit cache)"
curl -X GET "$BASE_URL/products/user/1" | jq '.'