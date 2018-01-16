import pytest
from hamcrest import *
from pet_store_client import PetStoreClient


def test_place_order(pet_context, pet_store_client):
    order = {
        "petId": pet_context['id'],
        "status": "Placed",
        "complete": False
    }
    response = pet_store_client.place_order(order)

    order = response.json()
    assert_that(order['status'], is_('Placed'))
    assert_that(order['complete'], is_(False))
    assert_that(order['id'], is_(not_none()))
    assert_that(order['shipDate'], is_(none()))