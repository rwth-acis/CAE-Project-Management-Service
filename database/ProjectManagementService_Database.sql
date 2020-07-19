--
-- Database:  commedit 
-- Creates the CAE datatabase structure needed to realize the project management.
-- --------------------------------------------------------

--
-- Table structure for table Project.
--
CREATE TABLE IF NOT EXISTS commedit.Project (
  id INT NOT NULL AUTO_INCREMENT,
  gitHubProjectId INT,
  gitHubProjectHtmlUrl VARCHAR(255),
  name VARCHAR(255) NOT NULL,
  CONSTRAINT projectPK PRIMARY KEY (id)
);

--
-- Table structure for table Role.
--
CREATE TABLE IF NOT EXISTS commedit.Role (
  id INT NOT NULL AUTO_INCREMENT,
  projectId INT NOT NULL,
  name VARCHAR(255) NOT NULL,
  is_default BOOLEAN NOT NULL,
  CONSTRAINT rolePK PRIMARY KEY (id),
  CONSTRAINT roleProjectFK FOREIGN KEY (projectId) REFERENCES commedit.Project(id) ON DELETE CASCADE
);

--
-- Table structure for table User.
--
CREATE TABLE IF NOT EXISTS commedit.User (
  id INT NOT NULL AUTO_INCREMENT,
  loginName VARCHAR(255) NOT NULL,
  email VARCHAR(255) NOT NULL,
  gitHubUsername VARCHAR(255),
  CONSTRAINT userPK PRIMARY KEY (id)
);

--
-- Table structure for table ProjectToUser
--
CREATE TABLE IF NOT EXISTS commedit.ProjectToUser (
  id INT NOT NULL AUTO_INCREMENT,
  projectId INT NOT NULL,
  userId INT NOT NULL,
  CONSTRAINT projectToUserPK PRIMARY KEY (id),
  CONSTRAINT projectToUserProjectFK FOREIGN KEY (projectId) REFERENCES commedit.Project(id) ON DELETE CASCADE,
  CONSTRAINT projectToUserUserFK FOREIGN KEY (userId) REFERENCES commedit.User(id) ON DELETE CASCADE
);

--
-- Table structure for table UserToRole.
--
CREATE TABLE IF NOT EXISTS commedit.UserToRole (
  id INT NOT NULL AUTO_INCREMENT,
  userId INT NOT NULL,
  roleId INT NOT NULL,
  projectToUserId INT NOT NULL,
  CONSTRAINT userToRolePK PRIMARY KEY (id),
  CONSTRAINT userToRoleUserFK FOREIGN KEY (userId) REFERENCES commedit.User(id) ON DELETE CASCADE,
  CONSTRAINT userToRoleRoleFK FOREIGN KEY (roleId) REFERENCES commedit.Role(id) ON DELETE CASCADE,
  CONSTRAINT userToRoleProjectToUserFK FOREIGN KEY (projectToUserId) REFERENCES commedit.ProjectToUser(id) ON DELETE CASCADE
);

--
-- Table structure for table Component.
--
CREATE TABLE IF NOT EXISTS commedit.Component (
  id INT NOT NULL AUTO_INCREMENT,
  name VARCHAR(255) NOT NULL,
  type VARCHAR(255) NOT NULL,
  versionedModelId INT NOT NULL,
  reqBazProjectId INT,
  reqBazCategoryId INT,
  CONSTRAINT componentPK PRIMARY KEY (id),
  CONSTRAINT componentVersionedModelFK FOREIGN KEY (versionedModelId) REFERENCES commedit.VersionedModel(id) ON DELETE CASCADE
);

--
-- Table structure for table ProjectToComponent.
--
CREATE TABLE IF NOT EXISTS commedit.ProjectToComponent (
  id INT NOT NULL AUTO_INCREMENT,
  projectId INT NOT NULL,
  componentId INT NOT NULL,
  CONSTRAINT projectToComponentPK PRIMARY KEY (id),
  CONSTRAINT projectToComponentProjectFK FOREIGN KEY (projectId) REFERENCES commedit.Project(id) ON DELETE CASCADE,
  CONSTRAINT projectToComponentComponentFK FOREIGN KEY (componentId) REFERENCES commedit.Component(id) ON DELETE CASCADE
);

--
-- Table structure for table ProjectInvitation.
--
CREATE TABLE IF NOT EXISTS commedit.ProjectInvitation (
  id INT NOT NULL AUTO_INCREMENT,
  projectId INT NOT NULL,
  userId INT NOT NULL,
  CONSTRAINT projectInvitationPK PRIMARY KEY (id),
  CONSTRAINT projectInvitationProjectFK FOREIGN KEY (projectId) REFERENCES commedit.Project(id) ON DELETE CASCADE,
  CONSTRAINT projectInvitationUserFK FOREIGN KEY (userId) REFERENCES commedit.User(id) ON DELETE CASCADE
);

--
-- Table structure for table Dependency.
--
CREATE TABLE IF NOT EXISTS commedit.Dependency (
  id INT NOT NULL AUTO_INCREMENT,
  projectId INT NOT NULL,
  componentId INT NOT NULL,
  CONSTRAINT dependencyPK PRIMARY KEY (id),
  CONSTRAINT dependencyProjectFK FOREIGN KEY (projectId) REFERENCES commedit.Project(id) ON DELETE CASCADE,
  CONSTRAINT dependencyComponentFK FOREIGN KEY (componentId) REFERENCES commedit.Component(id) ON DELETE CASCADE
);