import pytest
from pet_store_client import PetStoreClient

@pytest.fixture(scope="session")
def pet_store_client(request):
    return PetStoreClient()


@pytest.fixture(scope="session")
def pet_context(request, pet_store_client):
    pet = {
        "name": "Harry",
        "category": "Cat",
        "bio": "I am fuzzy",
        "status": "Available",
        "tags": [],
        "photoUrls": []
    }

    response = pet_store_client.create_pet(pet)
    saved_pet = response.json()

    def fin():
        pet_store_client.delete_pet(saved_pet['id'])

    request.addfinalizer(fin)

    return saved_pet