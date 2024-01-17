<!--
Please don't remove this template and make sure you follow the checklist below. Not following these checks
will make your PR be rejected.
This checklist is based on https://cloudbees.atlassian.net/wiki/spaces/CORE/pages/3660120078/How+Roadrunner+team+works, 
specially on https://cloudbees.atlassian.net/wiki/spaces/CORE/pages/3660415220/Git+Github+usage+and+conventions+in+Roadrunner+team
and https://cloudbees.atlassian.net/wiki/spaces/CORE/pages/3671195766/Roadrunner+development+process
-->


# Submitter checklist

- [ ] This PR is created from my own fork and not from a branch on the repository.
- [ ] All commits are signed. In case of needing help, there are some useful links [on Roadrunners confluence page](https://cloudbees.atlassian.net/wiki/spaces/CORE/pages/3671195766/Roadrunner+development+process#Signed-commits-%26-GPG).
- [ ] After the PR is open, I haven't rebased and squashed. I understand that will make difficult for reviewers to follow the discussions. 
- [ ] Commit messages are self contained and descriptive.
- [ ] As this is a code change, there exists a JIRA ticket and the PR title starts with `[BEE-XXXX]` (Please notice the brackets).
- [ ] PR title is self-descriptive.
- [ ] The PR has a description explaining how this PR matches the acceptance criteria.
- [ ] The PR adds unit tests to cover the ticket.
- [ ] Testing notes are provided in the PR description containing manual testing or links to ATHs/integration tests, and explaining why the PR does not add unit tests.
- [ ] If the PR adds or updates the UI, the PR description includes screenshots.
- [ ] If the PR adds or updates the CLI or HTTP endpoints, the PR description includes instructions on how to execute them and examples of the outputs.
- [ ] Any special part that needs special attention, or it is harder to understand, has a comment.
- [ ] The JIRA ticket is `In progress` or `In review`.
- [ ] The release note information has been provided in the JIRA ticket.

<details>
  <summary><b>Before requesting review</b></summary>

PRs should, in general, make it easy for the reviewer, so remember:
* Sections that need special attention, doubts, design considerations should be remarked when opening a PR. It’s nice to have explanations in the description, but adding 
  comments directly in the code to review makes a real difference.
* Every book takes longer to be written than to be read, in case of doubt a complete answer avoids tons of not needed messages.

As a good practice when asking/answering in the PR try to be clear and give as many details as you can and avoid the monosyllabic / single sentence reply. As an example:

> Reviewer: You should use XXX pattern here.
> 
> Author: Why?

This would lead to a never ending message chain, “why” can be understood as “why would I do that, I don’t want to” or as “why should we, is that pattern going to add something I’m missing?” or as “why do we need that, I'm not familiar with that pattern” or as a ton of things. In this example both, the reviewer and the author could have explained what that pattern would provide, what would be the gain of applying it, why it was not applied, etc.
</details>

# Reviewer checklist

- [ ] As this is a code change, there exists a JIRA ticket and the PR title starts with `[BEE-XXXX]` (Please notice the brackets).
- [ ] PR title is self-descriptive.
- [ ] The PR matches the acceptance criteria.
- [ ] There are unit tests or a Testing notes section with a proper coverage.


<details>
  <summary><b>During the review</b></summary>
  As a good practice when asking/answering in the PR try to be clear and give as many details as you can and avoid the monosyllabic / single sentence reply. As an example:

> Reviewer: You should use XXX pattern here.
>
> Author: Why?

This would lead to a never ending message chain, “You should use XXX pattern here” might be an incomplete comment.
“You should use XXX pattern here because YYY” will help the author to contextualise the question and provide a well-built answer.
</details>

# Before merging
- [ ] All commits are signed. If not the case, the PR cannot be merged.
- [ ] After the PR was open, the PR hasn't been rebased and squashed.
- [ ] The JIRA ticket is `In progress` or `In review`.
- [ ] The release note information has been provided in the JIRA ticket.
- [ ] CI passes.
- [ ] All dependencies are final version (The PR doesn't have SNAPSHOT dependencies).
- [ ] Before merging, all questions / doubts must be addressed, either in the PR or in a new task, that must be created and linked to the PR.