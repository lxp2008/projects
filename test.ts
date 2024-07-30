// Import necessary modules and functions
import { createAzurePipelineAction } from './index';
import { createAzurePipeline } from './createAzurePipeline';
import { ScmIntegrationRegistry } from '@backstage/integration';
import { ConfigReader } from '@backstage/config';
import { getVoidLogger } from '@backstage/backend-common';
import { Octokit } from '@octokit/rest';

// Mock dependencies
jest.mock('./createAzurePipeline');
jest.mock('@backstage/integration');
jest.mock('@octokit/rest');

describe('createAzurePipelineAction', () => {
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

  it('should call createAzurePipeline with correct parameters', async () => {
    (createAzurePipeline as jest.Mock).mockResolvedValue({ pipelineId: 'pipeline-id' });

    const action = createAzurePipelineAction({
      integrations,
      config: new ConfigReader({}),
    });

    await action.handler(mockContext);

    expect(createAzurePipeline).toHaveBeenCalledWith(expect.objectContaining({
      input: expect.objectContaining({
        repositoryUrl: 'https://dev.azure.com/organization/project/_git/repo',
        branch: 'main',
        pipelineTemplate: 'template.yaml',
      }),
    }));
    expect(mockContext.output).toHaveBeenCalledWith('pipelineId', 'pipeline-id');
  });

  it('should throw an error for invalid parameters', async () => {
    const invalidContext = {
      ...mockContext,
      input: {
        repositoryUrl: 'invalid-url',
        branch: 'main',
        pipelineTemplate: 'template.yaml',
      },
    };

    const action = createAzurePipelineAction({
      integrations,
      config: new ConfigReader({}),
    });

    await expect(action.handler(invalidContext)).rejects.toThrow();
  });
});
