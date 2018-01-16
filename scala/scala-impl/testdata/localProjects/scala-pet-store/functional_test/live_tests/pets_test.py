import pytest
from hamcrest import *
from pet_store_client import PetStoreClient

def test_get_pet(pet_context, pet_store_client):
    response = pet_store_client.get_pet(pet_context['id'])

    pet = response.json()
    assert_that(pet['name'], is_('Harry'))
    assert_that(pet['category'], is_('Cat'))
    assert_that(pet['bio'], is_('I am fuzzy'))
    assert_that(pet['id'], is_(pet_context['id']))


def test_list_pets(pet_context, pet_store_client):
    response = pet_store_client.list_pets()

    pets = response.json()
    assert_that(pets, has_length(1))

    assert_that(pets[0]['name'], is_('Harry'))


def test_find_pets_by_status(pet_context, pet_store_client):
    response = pet_store_client.find_pets_by_status(['Available', 'Pending'])

    pets = response.json()
    assert_that(pets, has_length(1))

    assert_that(pets[0]['name'], is_('Harry'))

def test_find_pets_by_tags(pet_context, pet_store_client):
    # No Pets with "Amphibian" tags exist yet
    response = pet_store_client.find_pets_by_tag(['Amphibian'])
    pets = response.json()
    assert_that(pets, has_length(0))

    # Add a pet
    pet = {
        "name": "Nancy",
        "category": "Frog",
        "bio": "R-r-ribbit!",
        "status": "Pending",
        "tags": ["Green", "Amphibian", "Croaker"],
        "photoUrls": []
    }
    pet_store_client.create_pet(pet)

    # Grab all pets
    response = pet_store_client.find_pets_by_tag([''])
    pets = response.json()
    assert_that(pets, has_length(2))
    assert_that(pets[0]['name'], is_('Harry'))

    # Retry "Amphibian" tag - there should be exactly one now
    response = pet_store_client.find_pets_by_tag(['Amphibian'])
    pets = response.json()
    assert_that(pets, has_length(1))
    assert_that(pets[0]['name'],is_('Nancy'))

def test_update_pet(pet_store_client):
    pet = {
        "name": "JohnnyUpdate",
        "category": "Cat",
        "bio": "I am fuzzy",
        "status": "Available",
        "tags": ["Cat","Fuzzy","Fur ball"],
        "photoUrls": []
    }

    saved_pet = None

    try:
        response = pet_store_client.create_pet(pet)
        saved_pet = response.json()
        saved_pet['bio'] = "Not so fuzzy"

        response = pet_store_client.update_pet(saved_pet)
        updated_pet = response.json()

        assert_that(updated_pet['bio'], is_('Not so fuzzy'))
    finally:
        if saved_pet:
            pet_store_client.delete_pet(saved_pet['id'])


def test_update_pet_not_found(pet_store_client):
    pet = {
        "id": 99999999,
        "name": "NotFound",
        "category": "Cat",
        "bio": "I am fuzzy",
        "status": "Available",
        "tags": [],
        "photoUrls": []
    }
    response = pet_store_client.update_pet(pet)
    assert_that(response.status_code, is_(404))
