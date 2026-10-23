import {cleanupRegisteredFixtures} from '../../tools/test-fixture-registry.mjs'
export default async function teardown(){await cleanupRegisteredFixtures(process.env.TEST_RUN_ID)}
