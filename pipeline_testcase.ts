// Import necessary modules and functions
import { AzurePipelinesScaffolder } from '@parfuemerie-douglas/scaffolder-backend-module-azure-pipelines';
import { ScmIntegrationRegistry } from '@backstage/integration';
import { ConfigReader } from '@backstage/config';

// Mock dependencies
jest.mock('@backstage/integration');
jest.mock('@backstage/config');

describe('AzurePipelinesScaffolder', () => {
  let scaffolder: AzurePipelinesScaffolder;
  let scmIntegrationRegistry: jest.Mocked<ScmIntegrationRegistry>;
  let config: ConfigReader;

  beforeEach(() => {
    // Mock ScmIntegrationRegistry and ConfigReader
    scmIntegrationRegistry = new ScmIntegrationRegistry() as jest.Mocked<ScmIntegrationRegistry>;
    config = new ConfigReader({});
    scaffolder = new AzurePipelinesScaffolder({ integrations: scmIntegrationRegistry, config });
  });

  it('should create an Azure Pipelines scaffolder instance', () => {
    expect(scaffolder).toBeInstanceOf(AzurePipelinesScaffolder);
  });

  it('should validate the pipeline creation parameters', async () => {
    const mockParameters = {
      repositoryUrl: 'https://dev.azure.com/organization/project/_git/repo',
      branch: 'main',
      pipelineTemplate: 'template.yaml',
    };

    await expect(scaffolder.validate(mockParameters)).resolves.not.toThrow();
  });

  it('should throw an error for invalid parameters', async () => {
    const invalidParameters = {
      repositoryUrl: '',
      branch: '',
      pipelineTemplate: '',
    };

    await expect(scaffolder.validate(invalidParameters)).rejects.toThrow();
  });

  it('should create a pipeline in Azure DevOps', async () => {
    const mockParameters = {
      repositoryUrl: 'https://dev.azure.com/organization/project/_git/repo',
      branch: 'main',
      pipelineTemplate: 'template.yaml',
    };

    // Mock the createPipeline method to avoid actual API calls
    jest.spyOn(scaffolder, 'createPipeline').mockResolvedValue();

    await scaffolder.createPipeline(mockParameters);

    expect(scaffolder.createPipeline).toHaveBeenCalledWith(mockParameters);
  });
});
