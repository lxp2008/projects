// Import necessary modules and functions
import { createAzurePipeline } from '../../actions/run/createAzurePipeline';
import { getVoidLogger } from '@backstage/backend-common';
import { ScmIntegrationRegistry } from '@backstage/integration';
import { ConfigReader } from '@backstage/config';
import { Octokit } from '@octokit/rest';

// Mock dependencies
jest.mock('@backstage/integration');
jest.mock('@octokit/rest');

describe('createAzurePipeline', () => {
  const logger = getVoidLogger();
  const integrations = new ScmIntegrationRegistry();

  const mockContext = {
    input: {
      repositoryUrl: 'https://dev.azure.com/organization/project/_git/repo',
      branch: 'main',
      pipelineTemplate: 'template.yaml',
    },
    workspacePath: '/mock/workspace/path',
    logger,
    logStream: {
      write: jest.fn(),
    },
    output: jest.fn(),
    createTemporaryDirectory: jest.fn(),
  };

  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('should create an Azure Pipeline', async () => {
    // Mock Octokit
    const mockOctokit = {
      repos: {
        get: jest.fn().mockResolvedValue({ data: { default_branch: 'main' } }),
      },
      actions: {
        createOrUpdateRepoSecret: jest.fn(),
        createOrUpdateRepoSecretForRepo: jest.fn(),
      },
    };
    Octokit.mockImplementation(() => mockOctokit);

    // Mock ScmIntegrationRegistry
    jest.spyOn(integrations, 'resolveUrl').mockImplementation((url) => url);
    jest.spyOn(integrations, 'byUrl').mockReturnValue({
      config: { host: 'dev.azure.com' },
      resolveUrl: (url: string) => url,
      type: 'azure',
    });

    await createAzurePipeline(mockContext);

    // Assert that the function was called with the correct parameters
    expect(mockContext.output).toHaveBeenCalledWith('pipelineId', expect.any(String));
  });

  it('should throw an error for invalid repository URL', async () => {
    const invalidContext = {
      ...mockContext,
      input: {
        ...mockContext.input,
        repositoryUrl: 'invalid-url',
      },
    };

    await expect(createAzurePipeline(invalidContext)).rejects.toThrow('Invalid repository URL');
  });
});
