pragma solidity ^0.5.0;

contract TestCon {
    struct Service {
        uint256 id;
        string name;
        string author;
        string deployStatus;
    }
    mapping(bytes32 => Service) public services;

    function stringHash(string memory name) public pure returns (bytes32) {
        return keccak256(abi.encode(name));
    }

    // function myTestFc(string memory id) public view returns (string memory) {
    //     bytes32 hash = stringHash(id);
    //     return services[hash].author;
    // }

    // function myTestFc2(string memory id) public {
    //     bytes32 hash = stringHash(id);
    //     services[hash] = Service(id, id);
    // }

    function setDeployStatus(
        uint256 id,
        string memory name,
        string memory author,
        string memory deployStatus
    ) public {
        bytes32 hash = stringHash(name);
        services[hash] = Service(id, name, author, deployStatus);
    }
    function getDeployStatus(string memory name) public view returns (string memory) {
        bytes32 hash = stringHash(name);
        return services[hash].deployStatus;
    }

}
